package com.chiou.javabridge;

import com.chiou.javabridge.Models.IClientMessageHandler;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

public class Communicator {
    private final Logger _logger = JavaBridge.LOGGER;

    public final List<String> LoadedMods = new ArrayList<>();
    private final List<Process> launchedModProcesses = Collections.synchronizedList(new ArrayList<>());

    protected final Map<String, String> PendingResponses = new ConcurrentHashMap<>();
    protected final Object ResponseLock = new Object();

    // TODO: Dispose/stop on shutdown?
    private ServerSocket _serverSocket;
    private Socket _clientSocket;
    private BufferedReader _reader;
    private BufferedWriter _writer;

    private IClientMessageHandler _clientHandler;

    private final CommandHandler _commandHandler;

    public int LoadedModsCount = 0;

    public Communicator() {
        try {
            _logger.info("Starting TCP server on port " + 63982);
            _serverSocket = new ServerSocket(63982);

            _commandHandler = new CommandHandler(this);

            new Thread(this::acceptClient).start();

            findAndLaunchBridgeStartupFiles(63982);

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
            throw new RuntimeException(e);
        }
    }

    public void SetClientHandler(IClientMessageHandler handler) {
        _clientHandler = handler;
    }

    public CompletableFuture<String> waitForResponseAsync(String requestId, long timeoutMs) {
        CompletableFuture<String> future = new CompletableFuture<>();
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + timeoutMs;
            synchronized (ResponseLock) {
                while (!PendingResponses.containsKey(requestId)) {
                    long waitTime = deadline - System.currentTimeMillis();
                    if (waitTime <= 0) break;
                    try {
                        ResponseLock.wait(waitTime);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
                String response = PendingResponses.remove(requestId);
                future.complete(response);
            }
        }).start();
        return future;
    }

    private void handleIncoming(String line) throws IOException {
        String[] parts = line.split(":", 5);
        if (parts.length < 5) {
            System.err.println("Invalid message: " + line);
            return;
        }

        String guid = parts[0];
        String platform = parts[1];
        String handler = parts[2];
        String event = parts[3];
        String payload = parts[4];

        if (platform.equalsIgnoreCase("CLIENT")) {
            if (_clientHandler != null) {
                _clientHandler.handleClientMessage(guid, platform, handler, event, payload);
            } else {
                _logger.warn("Received CLIENT message but no client handler is registered");
            }
            return;
        }

        switch (handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(guid, platform, event, payload);

            case "SERVER" -> {
                if(event.equals("HELLO"))
                    handleNewMod(guid, payload);
            }
            default -> _logger.info("Unknown handler: " + handler);
        }
    }

    private void acceptClient() {
        try {
            _clientSocket = _serverSocket.accept();
            _logger.info("New ModHost connected.");

            _reader = new BufferedReader(new InputStreamReader(_clientSocket.getInputStream()));
            _writer = new BufferedWriter(new OutputStreamWriter(_clientSocket.getOutputStream()));

            SendToHost("SERVER:SERVER:HELLO:JavaMod");

            String line;
            while ((line = _reader.readLine()) != null) {
                handleIncoming(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected void SendSafe(String message) {
        try {
            SendToHost(message);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    protected synchronized void SendToHost(String message) throws IOException {
        if (_writer != null) {
            _writer.write(message);
            _writer.newLine();
            _writer.flush();
        }
    }

    private void handleNewMod(String guid, String payload) {
        _logger.info("A new mod has been registered: " + payload);
        LoadedMods.add(payload);
    }

    private Path getModsFolder() {
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
            _logger.error("Failed to scan mods directory for bridgeStartup files", e);
        }

        _logger.info("Loaded " + LoadedModsCount + " mods via JavaBridge.");
    }

    private void launchBridgeStartup(Path bridgeStartupFile, Path workingDirectory, int port) {
        try {
            List<String> lines = Files.readAllLines(bridgeStartupFile);
            if (lines.isEmpty()) {
                _logger.warn("Empty bridgeStartup file: " + bridgeStartupFile);
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
                        _logger.info("[BridgeMod] " + line);
                    }
                } catch (IOException e) {
                    _logger.error("Error reading bridgeStartup output", e);
                }
            }).start();
        } catch (IOException e) {
            _logger.error("Failed to launch bridgeStartup: " + bridgeStartupFile, e);
        }
    }
}
