package com.chiou.javabridge;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.resource.ResourcePackActivationType;
import net.fabricmc.fabric.impl.resource.loader.ModNioResourcePack;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.resource.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;

public class JavaBridge implements ModInitializer {
	public static final String MOD_ID = "java-bridge";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static JavaBridge INSTANCE;

	public static MinecraftServer Server;

	private Path _dynamicPack;
    {
        try {
            _dynamicPack = JavaBridge.getResourceFolder().resolve("javaBridgeDynamicPack");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected final Communicator Communicator = new Communicator(this::CopyModAssets);

    private void CopyModAssets(Path assetsDir) {
        try {
            copyDirectory(Path.of(assetsDir.toString(), "assets", "minecraft", "textures", "item"), Path.of(_dynamicPack.toString(), "assets", "minecraft", "textures", "item"));
        } catch (IOException e) {
			JavaBridge.LOGGER.error("Could not copy mod assets.", e);
        }
    }

	public void copyDirectory(Path sourceDir, Path targetDir) throws IOException {
		Files.walkFileTree(sourceDir, new SimpleFileVisitor<Path>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Path targetPath = targetDir.resolve(sourceDir.relativize(dir));
				if (!Files.exists(targetPath)) {
					Files.createDirectories(targetPath);
				}
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Path targetPath = targetDir.resolve(sourceDir.relativize(file));
				Files.copy(file, targetPath, StandardCopyOption.REPLACE_EXISTING);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	@Override
	public void onInitialize() {
		INSTANCE = this;

		try {
			Path resourcesFolder = JavaBridge.getResourceFolder();
			if (!Files.exists(resourcesFolder))
				Files.createDirectories(resourcesFolder);

			_dynamicPack = resourcesFolder.resolve("javaBridgeDynamicPack");

			FileUtils.deleteDirectory(new File(_dynamicPack.toUri()));
			Files.createDirectories(_dynamicPack);

			try (InputStream inputStream = getClass().getResourceAsStream("/resourcepacks/runtimepack/pack.mcmeta")) {
				if (inputStream == null) {
					LOGGER.error("pack.mcmeta not found in JAR");
				} else {
					Path targetFile = _dynamicPack.resolve("pack.mcmeta");
					Files.copy(inputStream, targetFile, StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			ServerLifecycleEvents.SERVER_STARTED.register(newServer -> Server = newServer);
			ServerLifecycleEvents.SERVER_STARTED.register(JavaBridge::RegisterServerPacks);
			registerListModsCommand();
		} catch (Exception e) {
			LOGGER.error("Something went wrong in the initialization.", e);
		}
	}

	private static void RegisterServerPacks(MinecraftServer server) {
		try {
			boolean modBundled = true;

			ModContainer modContainer = FabricLoader.getInstance().getModContainer(JavaBridge.MOD_ID).get();
			String dynamicPath = Path.of(JavaBridge.getResourceFolder().toString(), "javaBridgeDynamicPack").toString();

			Identifier id = Identifier.of(JavaBridge.MOD_ID, "dynamic_pack_id");
			Text displayName = Text.literal(id.getNamespace() + "/" + id.getPath());

			List<Path> paths = modContainer.getRootPaths();
			String separator = paths.getFirst().getFileSystem().getSeparator();
			dynamicPath = dynamicPath.replace("/", separator);

			ModNioResourcePack dataPack = ResourcePackHandler.CreatePack(id.toString(), modContainer, dynamicPath, ResourceType.SERVER_DATA, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			if (dataPack == null) {
				JavaBridge.LOGGER.error("Failed to create resource packs.");
			}

			String packId = dynamicPath != null && modBundled ? id + "_" + dynamicPath : id.toString();


			ResourcePackProfile.PackFactory dataFactory = ResourcePackHandler.CreateFactory(dataPack);


			ResourcePackInfo metadata = ResourcePackHandler.CreatePackInfo(packId, displayName, modContainer);

			ResourcePackInfo dataMetadata = ResourcePackHandler.CreatePackInfo(packId + "_data", Text.literal("Dynamic Data Pack"), modContainer);

			ResourcePackProvider dataProvider = ResourcePackHandler.CreateProvider(dataMetadata, dataFactory, ResourceType.SERVER_DATA);


			Field providersField = ResourcePackManager.class.getDeclaredField("providers");
			providersField.setAccessible(true);

			Set<ResourcePackProvider> providers =
					(Set<ResourcePackProvider>) providersField.get(server.getDataPackManager());

			providers.add(dataProvider);

			server.getDataPackManager().enable("java-bridge");
//				client.reloadResources();
			JavaBridge.LOGGER.info("Dynamic resource pack was loaded in.");
		} catch (Exception e) {
			JavaBridge.LOGGER.error("An error occured while loading in the dynamic resource pack.", e);
		}
	}

	private void registerListModsCommand() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
			dispatcher.register(CommandManager.literal("bridgemods").executes(context -> {
            context.getSource().sendFeedback(() -> Text.literal("[" + Communicator.LoadedModsCount + "]" + String.join(",", Communicator.LoadedMods)), false);
            return 1;
        })));
	}

	public static Path getModsFolder() throws IOException {
		Path gameDir = FabricLoader.getInstance().getGameDir();

		String gameDirStr = gameDir.toString().toLowerCase();
		if (gameDirStr.contains("modrinthapp")) {
			Path modrinthMods = gameDir.resolve("mods");
			if (Files.isDirectory(modrinthMods)) {

				if (!Files.exists(modrinthMods))
				    Files.createDirectories(modrinthMods);

				return modrinthMods;
			}
		}

		Path modrinthProfilesDir = Path.of(System.getenv("APPDATA"), ".minecraft", "mods");

		if (!Files.exists(modrinthProfilesDir))
		    Files.createDirectories(modrinthProfilesDir);

		return modrinthProfilesDir;
	}

	public static Path getResourceFolder() throws IOException {
		Path gameDir = FabricLoader.getInstance().getGameDir();

		String gameDirStr = gameDir.toString().toLowerCase();
		if (gameDirStr.contains("modrinthapp")) {
			Path modrinthMods = gameDir.resolve("resourcepacks");
			if (Files.isDirectory(modrinthMods)) {

				if (!Files.exists(modrinthMods))
				    Files.createDirectories(modrinthMods);
				return modrinthMods;
			}
		}

		Path modrinthProfilesDir = Path.of(System.getenv("APPDATA"), ".minecraft", "resourcepacks");

		if (!Files.exists(modrinthProfilesDir))
			Files.createDirectories(modrinthProfilesDir);

		return modrinthProfilesDir;
	}
}