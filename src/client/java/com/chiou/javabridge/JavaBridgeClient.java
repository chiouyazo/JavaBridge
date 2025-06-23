package com.chiou.javabridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

public class JavaBridgeClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		JavaBridge.INSTANCE.Communicator.SetClientHandler(new ClientBridgeHandler());

		ClientLifecycleEvents.CLIENT_STARTED.register(JavaBridgeClient::RegisterClientPacks);
	}

	private static void RegisterClientPacks(MinecraftClient client) {
		try {
			boolean modBundled = true;

			ModContainer modContainer = FabricLoader.getInstance().getModContainer(JavaBridge.MOD_ID).get();
			String dynamicPath = Path.of(JavaBridge.getResourceFolder().toString(), "javaBridgeDynamicPack").toString();

			Identifier id = Identifier.of(JavaBridge.MOD_ID, "dynamic_pack_id");
			Text displayName = Text.literal(id.getNamespace() + "/" + id.getPath());

			List<Path> paths = modContainer.getRootPaths();
			String separator = paths.getFirst().getFileSystem().getSeparator();
			dynamicPath = dynamicPath.replace("/", separator);

			ModNioResourcePack resourcePack = ResourcePackHandler.CreatePack(id.toString(), modContainer, dynamicPath, ResourceType.CLIENT_RESOURCES, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			ModNioResourcePack dataPack = ResourcePackHandler.CreatePack(id.toString(), modContainer, dynamicPath, ResourceType.SERVER_DATA, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			if (resourcePack == null && dataPack == null) {
				JavaBridge.LOGGER.error("Failed to create resource packs.");
			}

			String packId = dynamicPath != null && modBundled ? id + "_" + dynamicPath : id.toString();


			ResourcePackProfile.PackFactory resourceFactory = ResourcePackHandler.CreateFactory(resourcePack);

			ResourcePackProfile.PackFactory dataFactory = ResourcePackHandler.CreateFactory(dataPack);


			ResourcePackInfo metadata = ResourcePackHandler.CreatePackInfo(packId, displayName, modContainer);

			ResourcePackInfo dataMetadata = ResourcePackHandler.CreatePackInfo(packId + "_data", Text.literal("Dynamic Data Pack"), modContainer);


			ResourcePackProvider resourceProvider = ResourcePackHandler.CreateProvider(metadata, resourceFactory, ResourceType.CLIENT_RESOURCES);

			ResourcePackProvider dataProvider = ResourcePackHandler.CreateProvider(dataMetadata, dataFactory, ResourceType.SERVER_DATA);


			Field providersField = ResourcePackManager.class.getDeclaredField("providers");
			providersField.setAccessible(true);

			Set<ResourcePackProvider> providers =
					(Set<ResourcePackProvider>) providersField.get(client.getResourcePackManager());

			providers.add(resourceProvider);
			providers.add(dataProvider);

			client.getResourcePackManager().enable("java-bridge");
			client.reloadResources();
			JavaBridge.LOGGER.info("Dynamic resource pack was loaded in.");
		} catch (Exception e) {
			JavaBridge.LOGGER.error("An error occured while loading in the dynamic resource pack.", e);
		}
	}
}