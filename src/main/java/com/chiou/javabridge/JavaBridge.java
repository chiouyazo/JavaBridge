package com.chiou.javabridge;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.*;

import java.net.Socket;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class JavaBridge implements ModInitializer {
	public static final String MOD_ID = "java-bridge";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static int LoadedModsCount = 0;

	// TODO: Dispose/stop on shutdown?
	private ServerSocket _serverSocket;
	private Socket _clientSocket;
	private BufferedReader _reader;
	private BufferedWriter _writer;

	private final Map<String, String> commandGuidMap = new ConcurrentHashMap<>();

	private final List<String> _loadedMods = new ArrayList<>();
	private final List<Process> launchedModProcesses = Collections.synchronizedList(new ArrayList<>());

	public static final Map<String, ServerCommandSource> PendingCommands = new ConcurrentHashMap<>();

	private final Map<String, String> pendingResponses = new ConcurrentHashMap<>();
	private final Object responseLock = new Object();

	private static MinecraftServer server;

	private final DynamicCommandRegistrar registrar = new DynamicCommandRegistrar((guid, commandName, payload) -> {
		try {
			sendToCSharp(guid + ":COMMAND_EXECUTED:" + commandName + ":" + payload);
		} catch (IOException e) {
			LOGGER.error("Failed to send command executed event", e);
		}
	});

	private final BridgeRequirementChecker _requirementChecker = new BridgeRequirementChecker(this);

	@Override
	public void onInitialize() {
		try {
			ServerLifecycleEvents.SERVER_STARTED.register(newServer -> {
				server = newServer;
            });

			_serverSocket = new ServerSocket(63982);
			LOGGER.info("Starting TCP server on port " + 63982);

			new Thread(this::acceptClient).start();
			findAndLaunchBridgeStartupFiles(63982);

			registerListModsCommand();

			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				for (Process p : launchedModProcesses) {
					if (p.isAlive()) {
						p.destroy();
						try {
							p.waitFor(2, TimeUnit.SECONDS);
						} catch (InterruptedException ignored) {}
						if (p.isAlive()) {
							p.destroyForcibly();
						}
					}
				}
			}));

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void registerListModsCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("bridgemods").executes(context -> {
				context.getSource().sendFeedback(() -> Text.literal(String.join(",", _loadedMods)), false);
				return 1;
			}));
		});
	}

	private void acceptClient() {
		try {
			_clientSocket = _serverSocket.accept();
			LOGGER.info("New ModHost connected.");

			_reader = new BufferedReader(new InputStreamReader(_clientSocket.getInputStream()));
			_writer = new BufferedWriter(new OutputStreamWriter(_clientSocket.getOutputStream()));

			sendToCSharp("HELLO:JavaMod");

			String line;
			while ((line = _reader.readLine()) != null) {
				handleIncoming(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void handleIncoming(String line) throws IOException {
		String[] parts = line.split(":", 3);
		if (parts.length < 3) {
			System.err.println("Invalid message: " + line);
			return;
		}

		String guid = parts[0];
		String event = parts[1];
		String payload = parts[2];

		switch (event) {
			case "REGISTER_COMMAND" -> handleRegisterCommand(guid, payload);
			case "EXECUTE_COMMAND" -> handleExecuteCommand(guid, payload);
			case "COMMAND_FEEDBACK" -> handleCommandFeedback(guid, payload);
			case "COMMAND_FINALIZE" -> handleCommandFinalize(guid, payload);
			case "COMMAND_REQUIREMENT_RESPONSE" -> {
				pendingResponses.put(guid, payload);
				synchronized (responseLock) {
					responseLock.notifyAll();
				}
			}

			case "QUERY_COMMAND_SOURCE" -> handleCommandSourceQuery(guid, payload);
			case "HELLO" -> handleNewMod(guid, payload);
			default -> LOGGER.info("Unknown event: " + event);
		}
	}

	public CompletableFuture<String> waitForResponseAsync(String requestId, long timeoutMs) {
		CompletableFuture<String> future = new CompletableFuture<>();
		new Thread(() -> {
			long deadline = System.currentTimeMillis() + timeoutMs;
			synchronized (responseLock) {
				while (!pendingResponses.containsKey(requestId)) {
					long waitTime = deadline - System.currentTimeMillis();
					if (waitTime <= 0) break;
					try {
						responseLock.wait(waitTime);
					} catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						break;
					}
				}
				String response = pendingResponses.remove(requestId);
				future.complete(response);
			}
		}).start();
		return future;
	}

	private void handleCommandSourceQuery(String guid, String payload) throws IOException {
		String[] split = payload.split(":", 4);
		String commandId = split.length > 0 ? split[0] : "";
		String commandName = split.length > 1 ? split[1] : "";
		String query = split.length > 2 ? split[2] : "";
		// TODO: Validate that this is the correct required type inside (e.g. int)
		String additionalQuery = split.length > 3 ? split[3] : "";

		ServerCommandSource source = PendingCommands.get(commandId);
		// When a world is loaded, all permissions are checked, thus we need this source temporarily
		if(source == null) {
			if(_requirementChecker.CommandSources.containsKey(commandName))
				source = _requirementChecker.CommandSources.remove(commandName);
		}
		if (source != null) {
			String finalValue = "";

			switch (query) {
				case "IS_PLAYER" -> finalValue = String.valueOf(source.isExecutedByPlayer());
				case "NAME" -> finalValue = String.valueOf(source.getName());
				case "HASPERMISSIONLEVEL" -> finalValue = String.valueOf(source.hasPermissionLevel(Integer.parseInt(additionalQuery)));
			}

			sendToCSharp(guid + ":COMMAND_SOURCE_RESPONSE:" + commandId + ":" + finalValue);
		} else {
			LOGGER.warn("No pending command context for guid: " + commandId);
			sendToCSharp(guid + ":COMMAND_FEEDBACK:" + commandId + ":Error");
		}
	}

	private void handleCommandFinalize(String guid, String commandId) throws IOException {
		PendingCommands.remove(commandId);
		sendToCSharp(guid + ":COMMAND_FINALIZE:" + commandId + ":OK");
	}

	private void handleCommandFeedback(String guid, String payload) throws IOException {
		String[] split = payload.split(":", 2);
		String message = split[1];
		String commandId = split[0];

		ServerCommandSource source = PendingCommands.get(commandId);
		if (source != null) {
			source.sendFeedback(() -> Text.literal(message), false);

			sendToCSharp(guid + ":COMMAND_FEEDBACK:" + commandId + ":OK");
        } else {
			LOGGER.warn("No pending command context for guid: " + commandId);
			sendToCSharp(guid + ":COMMAND_FEEDBACK:" + commandId + ":Error");
		}
	}

	private void handleNewMod(String guid, String payload) {
		LOGGER.info("A new mod has been registered: " + payload);
		_loadedMods.add(payload);
	}

	private void handleRegisterCommand(String guid, String commandDef) throws IOException {
		String commandName = commandDef.split("\\|")[0];
		commandGuidMap.put(commandName, guid);

		registrar.registerCommand(commandDef, _requirementChecker);

		sendToCSharp(guid + ":COMMAND_REGISTERED:" + commandDef);
	}

	private void handleExecuteCommand(String guid, String command) {
		if (server == null) {
			sendSafe(guid + ":COMMAND_RESULT:Server not available");
			return;
		}

		server.execute(() -> {
			try {
				ServerCommandSource source = server.getCommandSource();
				server.getCommandManager().executeWithPrefix(source, command);
				sendSafe(guid + ":COMMAND_RESULT:Success");
			} catch (Exception e) {
				sendSafe(guid + ":COMMAND_RESULT:Error:" + e.getMessage());
			}
		});
	}

	private void sendCommandExecuted(String commandName, String payload) throws IOException {
		String guid = commandGuidMap.get(commandName);
		if (guid != null) {
			sendToCSharp(guid + ":COMMAND_EXECUTED:" + commandName + ":" + payload);
		} else {
			LOGGER.warn("No guid found for command " + commandName);
		}
	}

	private void sendSafe(String message) {
		try {
			sendToCSharp(message);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	protected synchronized void sendToCSharp(String message) throws IOException {
		if (_writer != null) {
			_writer.write(message);
			_writer.newLine();
			_writer.flush();
		}
	}

	private void findAndLaunchBridgeStartupFiles(int port) {
		Path modsFolder = getModsFolder();

		// TODO: Filter out mods that failed to laod
		LoadedModsCount = 0;
		try (DirectoryStream<Path> topLevel = Files.newDirectoryStream(modsFolder)) {
			for (Path path : topLevel) {
				if (Files.isRegularFile(path) && path.getFileName().toString().equals("bridgeStartup")) {
					// Run from mods folder
					launchBridgeStartup(path, modsFolder, port);
					LoadedModsCount++;
				} else if (Files.isDirectory(path)) {
					// Search for any file ending with .bridgeStartup inside the subfolder (only one level deep)
					try (var stream = Files.list(path)) {
						for (Path subFile : (Iterable<Path>) stream::iterator) {
							if (Files.isRegularFile(subFile) && subFile.getFileName().toString().endsWith(".bridgeStartup")) {
								launchBridgeStartup(subFile, path, port);
								LoadedModsCount++;
								break;
							}
						}
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		} catch (IOException e) {
			LOGGER.error("Failed to scan mods directory for bridgeStartup files", e);
		}

		LOGGER.info("Loaded " + LoadedModsCount + " mods via JavaBridge.");
	}

	public Path getModsFolder() {
		Path gameDir = FabricLoader.getInstance().getGameDir();

		String gameDirStr = gameDir.toString().toLowerCase();
		if (gameDirStr.contains("modrinthapp")) {
			Path modrinthMods = gameDir.resolve("mods");
			if (Files.isDirectory(modrinthMods)) {
				return modrinthMods;
			}
		}

		Path modrinthProfilesDir = Path.of(System.getenv("APPDATA"), ".minecraft", "mods");
		return modrinthProfilesDir;
	}

	private void launchBridgeStartup(Path bridgeStartupFile, Path workingDirectory, int port) {
		try {
			List<String> lines = Files.readAllLines(bridgeStartupFile);
			if (lines.isEmpty()) {
				LOGGER.warn("Empty bridgeStartup file: " + bridgeStartupFile);
				return;
			}

			List<String> commandParts = new ArrayList<>(List.of(lines.get(0).split(" ")));

			// Append the port as the last argument
			commandParts.add(Integer.toString(port));

			ProcessBuilder pb = new ProcessBuilder(commandParts);
			pb.directory(workingDirectory.toFile());
			pb.redirectErrorStream(true);

			Process process = pb.start();
			launchedModProcesses.add(process);

			new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						LOGGER.info("[BridgeMod] " + line);
					}
				} catch (IOException e) {
					LOGGER.error("Error reading bridgeStartup output", e);
				}
			}).start();
		} catch (IOException e) {
			LOGGER.error("Failed to launch bridgeStartup: " + bridgeStartupFile, e);
		}
	}

	private static void deleteDirectoryRecursively(Path path) throws IOException {
		if (Files.exists(path)) {
			Files.walk(path)
					.sorted(Comparator.reverseOrder())
					.forEach(p -> {
						try {
							Files.delete(p);
						} catch (IOException e) {
							e.printStackTrace();
						}
					});
		}
	}
}