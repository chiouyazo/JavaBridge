package com.chiou.javabridge;

import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.fabric.impl.resource.loader.ModResourcePackCreator;
import net.fabricmc.fabric.impl.resource.loader.ResourceManagerHelperImpl;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.registry.VersionedIdentifier;
import net.minecraft.resource.*;
import net.minecraft.text.Text;
import net.minecraft.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.nio.file.Files.exists;

public class ResourcePackHandler {
    public static @NotNull ResourcePackProvider CreateProvider(ResourcePackInfo metadata, ResourcePackProfile.PackFactory resourceFactory, ResourceType type) {
        ResourcePackProvider resourceProvider = consumer -> {
            try {
                ResourcePackProfile newProfile = ResourcePackProfile.create(
                        metadata,
                        resourceFactory,
                        type,
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
        return resourceProvider;
    }

    public static @NotNull ResourcePackInfo CreatePackInfo(String packId, Text displayName, ModContainer modContainer) {
        ResourcePackInfo metadata = new ResourcePackInfo(
                packId,
                displayName,
                ModResourcePackCreator.RESOURCE_PACK_SOURCE,
                Optional.of(new VersionedIdentifier(ModResourcePackCreator.FABRIC, packId, modContainer.getMetadata().getVersion().getFriendlyString()))
        );
        return metadata;
    }

    public static ResourcePackProfile.@NotNull PackFactory CreateFactory(ModNioResourcePack resourcePack) {
        ResourcePackProfile.PackFactory resourceFactory = new ResourcePackProfile.PackFactory() {
            @Override
            public ResourcePack open(ResourcePackInfo info) {
                return resourcePack;
            }

            @Override
            public ResourcePack openWithOverlays(ResourcePackInfo info, ResourcePackProfile.Metadata metadata) {
                return resourcePack;
            }
        };
        return resourceFactory;
    }

    public static void AddToBuildInPacks(Text displayName, ModNioResourcePack pack) throws NoSuchFieldException, IllegalAccessException {
        Field field = ResourceManagerHelperImpl.class.getDeclaredField("builtinResourcePacks");
        field.setAccessible(true);

        Set<Pair<Text, ModNioResourcePack>> set = (Set<Pair<Text, ModNioResourcePack>>) field.get(null);
        set.add(new Pair<>(displayName, pack));
    }

    public static ModNioResourcePack CreatePack(String id, ModContainer mod, String subPath, ResourceType type, ResourcePackActivationType activationType, boolean modBundled) {
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
