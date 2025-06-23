package com.chiou.javabridge;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.VersionedIdentifier;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Pair;


import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.*;

import static java.nio.file.Files.exists;

public class JavaBridgeClient implements ClientModInitializer {
	private static final Identifier EXAMPLE_LAYER = Identifier.of(JavaBridge.MOD_ID, "hud-example-layer");

	@Override
	public void onInitializeClient() {
		JavaBridge.Communicator.SetClientHandler(new ClientBridgeHandler());

		ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
			try {
				boolean modBundled = true;


				ModContainer modContainer = FabricLoader.getInstance().getModContainer(JavaBridge.MOD_ID).get();
				String dynamicPath = Path.of(JavaBridge.getResourceFolder().toString(), "dynamicPack").toString();
	//			Path dynamicPack = Path.of(FabricLoader.getInstance().getGameDir().toString(), "../../../", "dynamicPack");

				Identifier id = Identifier.of(JavaBridge.MOD_ID, "dynamic_pack_id");
				Text displayName = Text.literal(id.getNamespace() + "/" + id.getPath());

				List<Path> paths = modContainer.getRootPaths();
				String separator = paths.getFirst().getFileSystem().getSeparator();
				dynamicPath = dynamicPath.replace("/", separator);

				ModNioResourcePack resourcePack = create(id.toString(), modContainer, dynamicPath, ResourceType.CLIENT_RESOURCES, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

				ModNioResourcePack dataPack = create(id.toString(), modContainer, dynamicPath, ResourceType.SERVER_DATA, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

				if (resourcePack == null && dataPack == null) {
					JavaBridge.LOGGER.error("Failed to create resource packs.");
				}

				ResourcePackProfile.PackFactory factory = new ResourcePackProfile.PackFactory() {
					@Override
					public ResourcePack open(ResourcePackInfo info) {
						return resourcePack;
					}

					@Override
					public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
						return resourcePack;
					}
				};

				String packId = dynamicPath != null && modBundled ? id + "_" + dynamicPath : id.toString();

				ResourcePackInfo metadata = new ResourcePackInfo(
						packId,
						displayName,
						ModResourcePackCreator.RESOURCE_PACK_SOURCE,
						Optional.of(new VersionedIdentifier(ModResourcePackCreator.FABRIC, packId, modContainer.getMetadata().getVersion().getFriendlyString()))
				);

				ResourcePackProvider provider = consumer -> {
					try {
						ResourcePackProfile newProfile = ResourcePackProfile.create(
								metadata,
								factory,
								ResourceType.CLIENT_RESOURCES,
								new ResourcePackPosition(true, ResourcePackProfile.InsertionPosition.TOP, true)
						);

						if (newProfile != null) {
							consumer.accept(newProfile);

							JavaBridge.LOGGER.info("Injected resource pack profile: " + newProfile.getId());
						} else {
							JavaBridge.LOGGER.warn("Failed to create resource pack profile (was null).");
						}
					} catch (Exception e) {
						JavaBridge.LOGGER.error("Error injecting custom resource pack", e);
					}
				};

				Field providersField = ResourcePackManager.class.getDeclaredField("providers");
				providersField.setAccessible(true);

				Set<ResourcePackProvider> providers =
						(Set<ResourcePackProvider>) providersField.get(MinecraftClient.getInstance().getResourcePackManager());


				providers.add(provider);

				MinecraftClient.getInstance().getResourcePackManager().enable("java-bridge");
				client.reloadResources();
				JavaBridge.LOGGER.info("Reflection is done.");
			} catch (NoSuchFieldException e) {
				throw new RuntimeException(e);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		});
	}

	private static void AddToBuildInPacks(Text displayName, ModNioResourcePack pack) throws NoSuchFieldException, IllegalAccessException {
		Field field = ResourceManagerHelperImpl.class.getDeclaredField("builtinResourcePacks");
		field.setAccessible(true);

		Set<Pair<Text, ModNioResourcePack>> set = (Set<Pair<Text, ModNioResourcePack>>) field.get(null);
		set.add(new Pair<>(displayName, pack));
	}

	public static ModNioResourcePack create(String id, ModContainer mod, String subPath, ResourceType type, ResourcePackActivationType activationType, boolean modBundled) {
		try {
			List<Path> rootPaths = mod.getRootPaths();
			List<Path> paths;

			if (subPath == null) {
				paths = rootPaths;
			} else {
				paths = new ArrayList<>(rootPaths.size());

				for (Path path : rootPaths) {
					path = path.toAbsolutePath().normalize();
					Path childPath = path.resolve(subPath.replace("/", path.getFileSystem().getSeparator())).normalize();

					// !childPath.startsWith(path) ||
					if (!exists(childPath)) {
						continue;
					}

					paths.add(childPath);
				}
			}

			if (paths.isEmpty()) return null;

			String packId = subPath != null && modBundled ? "dynamic_" + id + "_" + subPath : "dynamic_" + id;
			Text displayName = subPath == null
					? Text.translatable("pack.name.fabricMod", mod.getMetadata().getName())
					: Text.translatable("pack.name.fabricMod.subPack", mod.getMetadata().getName(), Text.translatable("resourcePack." + subPath + ".name"));

			ResourcePackInfo metadata = new ResourcePackInfo(
					packId,
					displayName,
					ModResourcePackCreator.RESOURCE_PACK_SOURCE,
					Optional.of(new VersionedIdentifier(ModResourcePackCreator.FABRIC, packId, mod.getMetadata().getVersion().getFriendlyString()))
			);

			Class<?> clazz = ModNioResourcePack.class;

			Constructor<?> ctor = clazz.getDeclaredConstructor(
					String.class,
					ModContainer.class,
					List.class,
					ResourceType.class,
					ResourcePackActivationType.class,
					boolean.class,
					ResourcePackInfo.class
			);
			ctor.setAccessible(true);

            ModNioResourcePack ret = (ModNioResourcePack) ctor.newInstance(packId, mod, paths, type, activationType, modBundled, metadata);

        	return ret.getNamespaces(type).isEmpty() ? null : ret;

		} catch (InstantiationException e) {
			throw new RuntimeException(e);
		} catch (IllegalAccessException e) {
			throw new RuntimeException(e);
		} catch (InvocationTargetException e) {
			throw new RuntimeException(e);
		} catch (NoSuchMethodException e) {
            throw new RuntimeException(e);
        }
    }
}