package com.chiou.javabridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;

public class JavaBridgeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			dispatcher.register(CommandManager.literal("MeowMeow").executes(context -> {
				MinecraftClient.getInstance().setScreen(
						new CustomScreen(Text.empty(), "Meow Meow Purrr")
				);
				return 1;
			}));
		});
	}

}