package com.chiou.javabridge;

import com.chiou.javabridge.Models.ClientContext;
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

    private final Map<String, ClientContext> _clients = new ConcurrentHashMap<>();

    public final List<String> LoadedMods = new ArrayList<>();
    private final List<Process> launchedModProcesses = Collections.synchronizedList(new ArrayList<>());

    protected final Map<String, String> PendingResponses = new ConcurrentHashMap<>();
    protected final Object ResponseLock = new Object();

    // TODO: Dispose/stop on shutdown?
    private ServerSocket _serverSocket;

    private IClientMessageHandler _clientHandler;

    private final ServerCommandHandler _commandHandler;

    public int LoadedModsCount = 0;

    public Communicator() {
        try {
            _logger.info("Starting TCP server on port " + 63982);
            _serverSocket = new ServerSocket(63982);

            _commandHandler = new ServerCommandHandler(this);

            new Thread(this::acceptClientsLoop).start();

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

    private void handleIncoming(String clientId, String line) throws IOException {
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
                _clientHandler.handleClientMessage(clientId, guid, platform, handler, event, payload);
            } else {
                _logger.warn("Received CLIENT message but no client handler is registered");
            }
            return;
        }

        switch (handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(clientId, guid, platform, event, payload);

            case "SERVER" -> {
                if (event.equals("HELLO")) {
                    handleNewMod(clientId, guid, payload);
                }
            }
            default -> _logger.info("Unknown handler: " + handler);
        }
    }

    private void acceptClientsLoop() {
        while (true) {
            try {
                Socket socket = _serverSocket.accept();
                new Thread(() -> HandleClient(socket)).start();
            } catch (IOException e) {
                _logger.error("Error accepting client connection", e);
            }
        }
    }

    private void HandleClient(Socket socket) {
        String clientId = UUID.randomUUID().toString();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()))) {

            _logger.info("New mod client connected: " + clientId);
            _clients.put(clientId, new ClientContext(clientId, socket, reader, writer));

            SendToHost(clientId, "SERVER:SERVER:HELLO:" + clientId);

            String line;
            while ((line = reader.readLine()) != null) {
                handleIncoming(clientId, line);
            }
        } catch (IOException e) {
            _logger.warn("Mod client disconnected: " + clientId + " - " + e.getMessage());
        } finally {
            _clients.remove(clientId);
            try {
                socket.close();
            } catch (IOException ignored) {}
        }
    }

    public void SendToHost(String clientId, String message) {
        if(clientId == null) {
            _logger.error("Could not send message to mod because clientId was null: " + message);
        }
        ClientContext ctx = _clients.get(clientId);
        if (ctx != null) {
            try {
                synchronized (ctx.Writer) {
                    ctx.Writer.write(message);
                    ctx.Writer.newLine();
                    ctx.Writer.flush();
                }
            } catch (IOException e) {
                _logger.warn("Failed to send to mod " + clientId + ": " + e.getMessage());
            }
        }
    }

    private void handleNewMod(String clientId, String guid, String modName) {
        _logger.info("New mod registered: " + modName + " (clientId=" + clientId + ")");
        _clients.computeIfPresent(clientId, (id, context) -> {
            context.modName = modName;
            return context;
        });
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
