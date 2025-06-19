package com.chiou.javabridge;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import java.net.Socket;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class JavaBridge implements ModInitializer {
	public static final String MOD_ID = "java-bridge";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private ServerSocket serverSocket;
	private Socket clientSocket;
	private BufferedReader reader;
	private BufferedWriter writer;

	private final Map<String, String> commandGuidMap = new ConcurrentHashMap<>();

	private static MinecraftServer server;

	private final DynamicCommandRegistrar registrar = new DynamicCommandRegistrar((commandName, payload) -> {
		try {
			sendCommandExecuted(commandName, payload);
		} catch (IOException e) {
			LOGGER.error("Failed to send command executed event", e);
		}
	});

	@Override
	public void onInitialize() {
		try {
			ServerLifecycleEvents.SERVER_STARTED.register(newServer -> {
				server = newServer;
			});

			serverSocket = new ServerSocket(0);
			int port = serverSocket.getLocalPort();
			LOGGER.info("Starting TCP server on port " + port);

			new Thread(this::acceptClient).start();

			launchModHost(port);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void acceptClient() {
		try {
			clientSocket = serverSocket.accept();
			LOGGER.info("ModHost connected.");

			reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
			writer = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

			sendToCSharp("HELLO:JavaMod");

			String line;
			while ((line = reader.readLine()) != null) {
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
			default -> LOGGER.info("Unknown event: " + event);
		}
	}

	private void handleRegisterCommand(String guid, String commandDef) throws IOException {
		String commandName = commandDef.split("\\|")[0];
		commandGuidMap.put(commandName, guid);

		registrar.registerCommand(commandDef);

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


	private synchronized void sendToCSharp(String message) throws IOException {
		if (writer != null) {
			writer.write(message);
			writer.newLine();
			writer.flush();
		}
	}

	private void launchModHost(int port) throws IOException {
		Path tempDir = Files.createTempDirectory("modhost");
		tempDir.toFile().deleteOnExit();

		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				deleteDirectoryRecursively(tempDir);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}));

		String[] files = { "ModHost.dll", "ModHost.runtimeconfig.json", "ModHost.deps.json" };

		for (String filename : files) {
			try (InputStream is = getClass().getResourceAsStream("/csharp-host/" + filename)) {
				if (is == null) throw new IOException("Missing resource " + filename);
				Path outPath = tempDir.resolve(filename);
				Files.copy(is, outPath, StandardCopyOption.REPLACE_EXISTING);
				outPath.toFile().deleteOnExit();
			}
		}

		Path modHostDll = tempDir.resolve("ModHost.dll");

		ProcessBuilder pb = new ProcessBuilder(
				"dotnet",
				modHostDll.toString(),
				Integer.toString(port)
		);
		pb.redirectErrorStream(true);

		Process process = pb.start();

		new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					LOGGER.info(line);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}).start();
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