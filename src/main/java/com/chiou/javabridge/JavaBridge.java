package com.chiou.javabridge;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JavaBridge implements ModInitializer {
	public static final String MOD_ID = "java-bridge";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static JavaBridge INSTANCE;

	public static MinecraftServer Server;

	protected static final Communicator Communicator = new Communicator();


	@Override
	public void onInitialize() {
		INSTANCE = this;

		ServerLifecycleEvents.SERVER_STARTED.register(newServer -> Server = newServer);
		registerListModsCommand();
	}

	private void registerListModsCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("bridgemods").executes(context -> {
            context.getSource().sendFeedback(() -> Text.literal("[" + Communicator.LoadedModsCount + "]" + String.join(",", Communicator.LoadedMods)), false);
            return 1;
        })));
	}
}