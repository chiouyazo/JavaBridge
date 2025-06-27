package com.chiou.javabridge;

import com.chiou.javabridge.Handlers.ResourcePackHandler;
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
import java.util.concurrent.CompletableFuture;

public class JavaBridgeClient implements ClientModInitializer {
	private Field _providersField;
	private List<ResourcePackProvider> _newProviders;

	private final CompletableFuture<Boolean> _completedPreparation = new CompletableFuture<>();

	@Override
	public void onInitializeClient() {
		JavaBridge.INSTANCE.Communicator.SetClientHandler(new ClientBridgeHandler());

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			_completedPreparation.thenAccept(success -> {
				if (!success)
					return;

				client.execute(() -> RegisterClientPacks(client));
			});
		});

		PrepareResourcePacks();
	}

	private void PrepareResourcePacks() {
		try {
			boolean modBundled = false;

			ModContainer modContainer = FabricLoader.getInstance().getModContainer(JavaBridge.MOD_ID).orElseThrow(() -> new RuntimeException("ModContainer not found!"));
			String dynamicPath = Path.of(JavaBridge.getResourceFolder().toString(), "javaBridgeDynamicPack").toString();

			Identifier id = Identifier.of("java-bridge", "java-bridge");
			Text displayName = Text.literal(id.getNamespace() + "/" + id.getPath());

			List<Path> paths = modContainer.getRootPaths();
			String separator = paths.getFirst().getFileSystem().getSeparator();
			dynamicPath = dynamicPath.replace("/", separator);

			ModNioResourcePack resourcePack = ResourcePackHandler.CreatePack(id.getNamespace(), modContainer, dynamicPath, ResourceType.CLIENT_RESOURCES, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			ModNioResourcePack dataPack = ResourcePackHandler.CreatePack(id.getNamespace(), modContainer, dynamicPath, ResourceType.SERVER_DATA, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			String packId = id.getNamespace();
			_newProviders = new ArrayList<>();

			if (resourcePack != null)
				_newProviders.add(ResourcePackHandler.CreateResourcePackProvider(resourcePack, packId, displayName, modContainer, ResourceType.CLIENT_RESOURCES));
			else
				JavaBridge.LOGGER.info("Could not create data pack for client.");

			if (dataPack != null)
				_newProviders.add(ResourcePackHandler.CreateResourcePackProvider(dataPack, packId + "_data", Text.literal("Dynamic Data Pack"), modContainer, ResourceType.SERVER_DATA));
			else
				JavaBridge.LOGGER.info("Could not create data pack for client.");

			_providersField = ResourcePackManager.class.getDeclaredField("providers");
			_providersField.setAccessible(true);

			_completedPreparation.complete(true);
		} catch (Exception e) {
			JavaBridge.LOGGER.error("Failed to prepare client resource packs.", e);
			_completedPreparation.complete(false);
		}
	}

	private void RegisterClientPacks(MinecraftClient client) {
		try {
			Set<ResourcePackProvider> providers = (Set<ResourcePackProvider>) _providersField.get(client.getResourcePackManager());

			providers.addAll(_newProviders);

			client.getResourcePackManager().enable("java-bridge");
			client.reloadResources().thenRun(() -> {
				JavaBridge.LOGGER.info("Dynamic resource pack was loaded in.");
				Set<String> namespaces = client.getResourceManager().getAllNamespaces();
				JavaBridge.LOGGER.info("Namespaces after reload: " + namespaces);

				// Confirm model exists
				Map<Identifier, Resource> models = client.getResourceManager()
						.findResources("models/item", id -> id.getPath().contains("suspicious_substance"));
				Map<Identifier, Resource> textures = client.getResourceManager()
						.findResources("textures/item", id -> id.getPath().contains("suspicious_substance"));

				JavaBridge.LOGGER.info("Found models: " + models.keySet());
				JavaBridge.LOGGER.info("Found textures: " + textures.keySet());
			});

		} catch (Exception e) {
			JavaBridge.LOGGER.error("An error occured while loading in the dynamic resource pack.", e);
		}
	}
}