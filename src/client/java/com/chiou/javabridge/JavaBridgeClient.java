package com.chiou.javabridge;

import net.fabricmc.api.ClientModInitializer;

public class JavaBridgeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JavaBridge.Communicator.SetClientHandler(new ClientBridgeHandler());
	}
}