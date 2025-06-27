package com.chiou.javabridge;

import com.chiou.javabridge.Handlers.ResourcePackHandler;
import com.google.gson.Gson;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.List;
import java.util.Set;

public class JavaBridge implements ModInitializer {
	public static final String MOD_ID = "java-bridge";
	public static final Gson Gson = new Gson();

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static JavaBridge INSTANCE;

	public static MinecraftServer Server;

    protected Communicator Communicator = new Communicator();

	@Override
	public void onInitialize() {
		INSTANCE = this;

		try {

			ServerLifecycleEvents.SERVER_STARTED.register(newServer -> Server = newServer);
//			ServerLifecycleEvents.SERVER_STARTED.register(JavaBridge::RegisterServerPacks);
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

			Identifier id = Identifier.of("java-bridge");
			Text displayName = Text.literal(id.getNamespace() + "/" + id.getPath());

			List<Path> paths = modContainer.getRootPaths();
			String separator = paths.getFirst().getFileSystem().getSeparator();
			dynamicPath = dynamicPath.replace("/", separator);

			ModNioResourcePack dataPack = ResourcePackHandler.CreatePack(id.toString(), modContainer, dynamicPath, ResourceType.SERVER_DATA, ResourcePackActivationType.ALWAYS_ENABLED, modBundled);

			if (dataPack == null) {
				JavaBridge.LOGGER.error("Failed to create resource packs for server.");
				return;
			}

			String packId = id.toString();


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
            context.getSource().sendFeedback(() -> Text.literal("[" + Communicator.LoadedModsCount + "] " + String.join(", ", Communicator.LoadedMods)), false);
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