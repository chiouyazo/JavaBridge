package com.chiou.javabridge;

import com.chiou.javabridge.Handlers.ServerBlockHandler;
import com.chiou.javabridge.Handlers.ServerCommandHandler;
import com.chiou.javabridge.Handlers.ServerItemHandler;
import com.chiou.javabridge.Models.ClientContext;
import com.chiou.javabridge.Models.IClientMessageHandler;
import com.chiou.javabridge.Models.Communication.MessageBase;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Stream;

public class Communicator {
    private final Logger _logger = JavaBridge.LOGGER;

    private final Map<String, ClientContext> _clients = new ConcurrentHashMap<>();

    public final List<String> LoadedMods = new ArrayList<>();
    private final List<Process> launchedModProcesses = Collections.synchronizedList(new ArrayList<>());

    public final Map<String, String> PendingResponses = new ConcurrentHashMap<>();
    public final Object ResponseLock = new Object();
    private Path _dynamicPack;
    public Runnable FinalizedModLoadingTextureCallback;

    // TODO: Dispose/stop on shutdown?
    private ServerSocket _serverSocket;

    private IClientMessageHandler _clientHandler;

    private ServerCommandHandler _commandHandler;
    public ServerItemHandler _itemHandler;
    public ServerBlockHandler _blockHandler;

    private Consumer<Path> _onNewModEvent;

    public int LoadedModsCount = 0;

    public Communicator() {
        try {
            _onNewModEvent = this::CopyModAssets;

            Path resourcesFolder = JavaBridge.getResourceFolder();
            if (!Files.exists(resourcesFolder))
                Files.createDirectories(resourcesFolder);

            _dynamicPack = resourcesFolder.resolve("javaBridgeDynamicPack");

            FileUtils.deleteDirectory(new File(_dynamicPack.toUri()));
            Files.createDirectories(_dynamicPack);

            try (InputStream inputStream = getClass().getResourceAsStream("/resourcepacks/runtimepack/pack.mcmeta")) {
                if (inputStream == null) {
                    JavaBridge.LOGGER.error("pack.mcmeta not found in JAR");
                } else {
                    Path targetFile = _dynamicPack.resolve("pack.mcmeta");
                    Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

            _logger.info("Starting TCP server on port " + 63982);
            _serverSocket = new ServerSocket(63982);

            _commandHandler = new ServerCommandHandler(this);
            _itemHandler = new ServerItemHandler(this);
            _blockHandler = new ServerBlockHandler(this);

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
        }
        catch (Exception ex) {

        }
    }

    private void CopyModAssets(Path assetsDir) {
        try {
            // TODO: Merge lang files
            copyFolder(Path.of(assetsDir.toString(), "assets", ".minecraft"), Path.of(_dynamicPack.toString(), "assets", ".minecraft"));
            copyFolder(Path.of(assetsDir.toString(), "data", ".minecraft"), Path.of(_dynamicPack.toString(), "data", ".minecraft"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "blockstates"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "blockstates"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "equipment"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "equipment"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "font"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "font"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "items"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "items"));
//
//			copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "lang"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "lang"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "models"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "models"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "particles"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "particles"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "post_effect"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "post_effect"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "shaders"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "shaders"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "texts"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "texts"));
//            copyFolder(Path.of(assetsDir.toString(), "assets", "minecraft", "textures"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "textures"));
            _itemHandler.BuildBakedItems(assetsDir);
        } catch (IOException e) {
            JavaBridge.LOGGER.error("Could not copy mod assets.", e);
        }
    }

    public  void copyFolder(Path src, Path dest) throws IOException {
        if (!Files.exists(src))
            return;

        if (!Files.exists(dest))
            Files.createDirectories(dest);

        try (Stream<Path> stream = Files.walk(src)) {
            stream.forEach(source -> copy(source, dest.resolve(src.relativize(source))));
        }
    }

    private void copy(Path source, Path dest) {
        try {
            Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException(e.getMessage(), e);
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
        Gson gson = new Gson();
        MessageBase message = gson.fromJson(line, MessageBase.class);

        if (message.Platform.equalsIgnoreCase("CLIENT")) {
            if (_clientHandler != null) {
                _clientHandler.handleClientMessage(clientId, message);
            } else {
                _logger.warn("Received CLIENT message but no client handler is registered");
            }
            return;
        }
        switch (message.Handler) {
            case "COMMAND" -> _commandHandler.HandleRequest(clientId, message);
            case "ITEM" -> _itemHandler.HandleRequest(clientId, message);
            case "BLOCK" -> _blockHandler.HandleRequest(clientId, message);

            case "SERVER" -> {
                if (message.Event.equals("HELLO")) {
                    handleNewMod(clientId, message.Id, message.GetPayload());
                }
            }
            default -> _logger.info("Unknown handler: " + message.Handler);
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

            MessageBase message = new MessageBase();
            message.Id = UUID.randomUUID().toString();
            message.Event = "HELLO";
            message.Handler = "SERVER";
            message.Platform = "SERVER";
            message.Payload = JavaBridge.Gson.toJsonTree(clientId);

            SendToHost(clientId, message);

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

    public void SendToHost(String clientId, MessageBase message) {
        if(clientId == null) {
            _logger.error("Could not send message to mod because clientId was null: " + message);
        }
        ClientContext ctx = _clients.get(clientId);
        if (ctx != null) {
            try {
                synchronized (ctx.Writer) {
                    String json = JavaBridge.Gson.toJson(message);
                    ctx.Writer.write(json);
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

    private void findAndLaunchBridgeStartupFiles(int port) throws IOException {
        Path modsFolder = JavaBridge.getModsFolder();

        // TODO: Filter out mods that failed to laod
        LoadedModsCount = 0;
        LoadedMods.clear();
        try (DirectoryStream<Path> topLevel = Files.newDirectoryStream(modsFolder)) {
            for (Path path : topLevel) {
                if (Files.isRegularFile(path) && path.getFileName().toString().equals("bridgeStartup")) {
                    // Run from mods folder
                    launchBridgeStartup(path, modsFolder, port);
                    LoadedModsCount++;
                    LoadedMods.add(path.getFileName().toString().replace(".bridgeStartup", ""));
                } else if (Files.isDirectory(path)) {
                    // Search for any file ending with .bridgeStartup inside the subfolder (only one level deep)
                    try (var stream = Files.list(path)) {
                        for (Path subFile : (Iterable<Path>) stream::iterator) {
                            if (Files.isRegularFile(subFile) && subFile.getFileName().toString().endsWith(".bridgeStartup")) {
                                launchBridgeStartup(subFile, path, port);
                                LoadedModsCount++;
                                LoadedMods.add(path.getFileName().toString().replace(".bridgeStartup", ""));
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
        finally {
//            FinalizedModLoadingTextureCallback.run();
        }

        _logger.info("Loaded " + LoadedModsCount + " mods via JavaBridge.");
    }

    private void launchBridgeStartup(Path bridgeStartupFile, Path workingDirectory, int port) {
        try {
            String modId = bridgeStartupFile.getFileName().toString().replace(".bridgeStartup", "");
            _onNewModEvent.accept(Path.of(workingDirectory.toString(), modId + "_assets"));
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
