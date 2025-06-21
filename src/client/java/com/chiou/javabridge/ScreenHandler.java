package com.chiou.javabridge;

import com.chiou.javabridge.Models.EventHandler;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ScreenHandler extends EventHandler {
    @Override
    public String GetPlatform() { return "CLIENT"; }
    @Override
    public String GetHandler() { return "SCREEN"; }

    private final Logger _logger = JavaBridge.LOGGER;

    private final Communicator _communicator;

    private final Map<String, Screen> _screenGuidMap = new ConcurrentHashMap<>();

    private String _currentScreen = null;

    public ScreenHandler(Communicator communicator) {
        _communicator = communicator;
    }

    public void HandleRequest(String clientId, String guid, String platform, String event, String payload) throws IOException {
        switch (event) {
            case "CURRENTSCREEN" -> handleCurrentScreen(clientId, guid);
            case "OVERWRITESCREEN" -> handleOverwriteScreen(clientId, guid, payload);
            case "REPLACESCREEN" -> handleReplaceScreen(clientId, guid, payload);
            case "REPLACEWITHEXISTING" -> handleReplaceWithExisting(clientId, guid, payload);
            case "CLOSESCREEN" -> handleCloseScreen(clientId, guid);
            case "DELETESCREEN" -> handleDeleteScreen(clientId, guid, payload);
            case "LISTSCREENS" -> handleListScreens(clientId, guid);

            default -> _logger.info("Unknown event: " + event);
        }
    }

    private void handleCurrentScreen(String clientId, String guid) throws IOException {
        _communicator.SendToHost(clientId, AssembleMessage(guid, "CURRENTSCREEN", _currentScreen));
    }

    /// New screen to overwrite current (or none at all) // SCREENID:SCREENINFOS
    private void handleOverwriteScreen(String clientId, String guid, String payload) throws IOException {
        if (_currentScreen != null) {
            _screenGuidMap.remove(_currentScreen);
        }

        CustomScreen newScreen = new CustomScreen(Text.literal("Meow " + guid), payload);
        _screenGuidMap.put(guid, newScreen);
        _currentScreen = guid;
        MinecraftClient.getInstance().setScreen(newScreen);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "OVERWRITESCREEN", payload));
    }

    /// Replace current // SCREENID:SCREENINFOS
    private void handleReplaceScreen(String clientId, String guid, String payload) throws IOException {
        CustomScreen newScreen = new CustomScreen(Text.literal("Meow " + guid), payload);
        _screenGuidMap.put(guid, newScreen);
        _currentScreen = guid;
        MinecraftClient.getInstance().setScreen(newScreen);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "REPLACESCREEN", payload));
    }

    // Replace current with already existing (doesnt delete either) // INTERACTIONID:OLDSCREENID (OLDSCREENID = the one that should show up again)
    private void handleReplaceWithExisting(String clientId, String guid, String payload) throws IOException {
        Screen newScreen = _screenGuidMap.get(payload);
        _currentScreen = payload;
        MinecraftClient.getInstance().setScreen(newScreen);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "REPLACEWITHEXISTING", payload));
    }

    // INTERACTIONID
    private void handleCloseScreen(String clientId, String guid) throws IOException {
        _currentScreen = null;
        MinecraftClient.getInstance().setScreen(null);

        _communicator.SendToHost(clientId, AssembleMessage(guid, "CLOSESCREEN", ""));
    }

    // INTERACTIONID:SCREENTODELETE
    private void handleDeleteScreen(String clientId, String guid, String payload) throws IOException {
        if(payload.equals(_currentScreen)) {
            _currentScreen = null;
            MinecraftClient.getInstance().setScreen(null);
        }

        _screenGuidMap.remove(payload);
        _communicator.SendToHost(clientId, AssembleMessage(guid, "DELETESCREEN", payload));
    }

    private void handleListScreens(String clientId, String guid) throws IOException {
        _communicator.SendToHost(clientId, AssembleMessage(guid, "LISTSCREENS", String.join("||", _screenGuidMap.keySet())));
    }
}