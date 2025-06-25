package com.chiou.javabridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.*;

public class JavaBridgeClient implements ClientModInitializer {

//	private final MinecraftClient _client = MinecraftClient.getInstance();

	@Override
	public void onInitializeClient() {
		JavaBridge.INSTANCE.Communicator.SetClientHandler(new ClientBridgeHandler());
//		JavaBridge.INSTANCE.Communicator.FinalizedModLoadingTextureCallback = this::RegisterClientPacks;
//		RegisterClientPacks();

		ClientLifecycleEvents.CLIENT_STARTED.register(this::RegisterClientPacks);
	}

	private void RegisterClientPacks(MinecraftClient client) {
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


			String packId = id.toString();

			List<ResourcePackProvider> newProviders = new ArrayList<>();

			if (resourcePack != null)
				newProviders.add(CreateResourcePackProvider(resourcePack, packId, displayName, modContainer, ResourceType.CLIENT_RESOURCES));
			else
				JavaBridge.LOGGER.info("Could not create data pack for client.");

			if (dataPack != null)
				newProviders.add(CreateResourcePackProvider(dataPack, packId + "_data", Text.literal("Dynamic Data Pack"), modContainer, ResourceType.SERVER_DATA));
			else
				JavaBridge.LOGGER.info("Could not create data pack for client.");

			Field providersField = ResourcePackManager.class.getDeclaredField("providers");
			providersField.setAccessible(true);

			Set<ResourcePackProvider> providers =
					(Set<ResourcePackProvider>) providersField.get(client.getResourcePackManager());

			providers.addAll(newProviders);

			client.getResourcePackManager().enable("java-bridge");
			client.reloadResources();
//			InputStream modelStream = client.getResourceManager()
//					.getResource(Identifier.of("java-bridge", "models/item/suspicious_substance.json"))
//					.orElseThrow()
//					.getInputStream();
			JavaBridge.INSTANCE.Communicator._itemHandler.initialize();
			JavaBridge.LOGGER.info("Dynamic resource pack was loaded in.");
		} catch (Exception e) {
			JavaBridge.LOGGER.error("An error occured while loading in the dynamic resource pack.", e);
		}
	}

	private static @NotNull ResourcePackProvider CreateResourcePackProvider(ModNioResourcePack resourcePack, String packId, Text displayName, ModContainer modContainer, ResourceType type) {
		ResourcePackProfile.PackFactory resourceFactory = ResourcePackHandler.CreateFactory(resourcePack);
		ResourcePackInfo metadata = ResourcePackHandler.CreatePackInfo(packId, displayName, modContainer);
		ResourcePackProvider resourceProvider = ResourcePackHandler.CreateProvider(metadata, resourceFactory, type);
		return resourceProvider;
	}
}