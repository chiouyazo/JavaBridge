package com.chiou.javabridge;

import com.chiou.javabridge.Models.Communication.MessageBase;
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

    public void HandleRequest(String clientId, MessageBase message) throws IOException {
        switch (message.Event) {
            case "CURRENTSCREEN" -> handleCurrentScreen(clientId, message.Id);
            case "OVERWRITESCREEN" -> handleOverwriteScreen(clientId, message);
            case "REPLACESCREEN" -> handleReplaceScreen(clientId, message);
            case "REPLACEWITHEXISTING" -> handleReplaceWithExisting(clientId, message);
            case "CLOSESCREEN" -> handleCloseScreen(clientId, message.Id);
            case "DELETESCREEN" -> handleDeleteScreen(clientId, message);
            case "LISTSCREENS" -> handleListScreens(clientId, message.Id);

            default -> _logger.info("Unknown event: " + message.Event);
        }
    }

    private void handleCurrentScreen(String clientId, String guid) throws IOException {
        if (_currentScreen != null &&  MinecraftClient.getInstance().currentScreen == null)
            _currentScreen = null;

        _communicator.SendToHost(clientId, AssembleMessage(guid, "CURRENTSCREEN", _currentScreen));
    }

    /// New screen to overwrite current (or none at all) // SCREENID:SCREENINFOS
    private void handleOverwriteScreen(String clientId, MessageBase message) throws IOException {
        if (_currentScreen != null) {
            _screenGuidMap.remove(_currentScreen);
        }

        CustomScreen newScreen = new CustomScreen(Text.literal("Meow " + message.Id), message.GetPayload());
        _screenGuidMap.put(message.Id, newScreen);
        _currentScreen = message.Id;
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(newScreen));

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "OVERWRITESCREEN", message.GetPayload()));
    }

    /// Replace current // SCREENID:SCREENINFOS
    private void handleReplaceScreen(String clientId, MessageBase message) throws IOException {
        CustomScreen newScreen = new CustomScreen(Text.literal("Meow " + message.Id), message.GetPayload());
        _screenGuidMap.put(message.Id, newScreen);
        _currentScreen = message.Id;
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(newScreen));

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "REPLACESCREEN", message.GetPayload()));
    }

    // Replace current with already existing (doesnt delete either) // INTERACTIONID:OLDSCREENID (OLDSCREENID = the one that should show up again)
    private void handleReplaceWithExisting(String clientId, MessageBase message) throws IOException {
        Screen newScreen = _screenGuidMap.get(message.GetPayload());
        _currentScreen = message.GetPayload();
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(newScreen));

        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "REPLACEWITHEXISTING", message.GetPayload()));
    }

    // INTERACTIONID
    private void handleCloseScreen(String clientId, String guid) throws IOException {
        _currentScreen = null;
        MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(null));

        _communicator.SendToHost(clientId, AssembleMessage(guid, "CLOSESCREEN", ""));
    }

    // INTERACTIONID:SCREENTODELETE
    private void handleDeleteScreen(String clientId, MessageBase message) throws IOException {
        if(message.GetPayload().equals(_currentScreen)) {
            _currentScreen = null;
            MinecraftClient.getInstance().execute(() -> MinecraftClient.getInstance().setScreen(null));
        }

        _screenGuidMap.remove(message.GetPayload());
        _communicator.SendToHost(clientId, AssembleMessage(message.Id, "DELETESCREEN", message.GetPayload()));
    }

    private void handleListScreens(String clientId, String guid) throws IOException {
        _communicator.SendToHost(clientId, AssembleMessage(guid, "LISTSCREENS", String.join("||", _screenGuidMap.keySet())));
    }
}