package io.github.tbenassi.com.hotdeposit.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.tbenassi.com.hotdeposit.client.mixin.MinecraftServerAccessor;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.server.integrated.IntegratedServer;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.UUID;

/**
 * Persists which containers are enabled/disabled for hot deposit.
 * Block containers are identified by BlockPos.asLong().
 * Entity containers are identified by UUID (persists across sessions).
 */
public class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String MOD_ID = "hot-deposit";
    public static final Path MOD_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);

    @Nullable
    private static Path currentStateFile;

    // Block containers use position (Long from BlockPos.asLong())
    private static HashSet<Long> disabledBlockContainers = new HashSet<>();

    // Entity containers use UUID (persists even when entity moves)
    private static HashSet<UUID> disabledEntityContainers = new HashSet<>();

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register(ClientState::initServerStateFile);
        ClientPlayConnectionEvents.DISCONNECT.register(ClientState::saveServerStateFile);
    }

    private static void initServerStateFile(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        IntegratedServer integratedServer = client.getServer();
        ServerInfo currentServerEntry = client.getCurrentServerEntry();
        String fileName;
        if (integratedServer != null) {
            fileName = ((MinecraftServerAccessor) integratedServer).getSession().getDirectoryName();
        } else if (currentServerEntry != null) {
            fileName = currentServerEntry.address.replace(":", "colon");
        } else {
            HotDepositClient.LOGGER.debug("Failed to get server or level name");
            return;
        }

        // Load block container state
        currentStateFile = MOD_CONFIG_DIR.resolve(fileName + "_blocks.json");
        try (BufferedReader reader = Files.newBufferedReader(currentStateFile)) {
            Type type = new TypeToken<HashSet<Long>>() {}.getType();
            disabledBlockContainers = GSON.fromJson(reader, type);
            if (disabledBlockContainers == null) disabledBlockContainers = new HashSet<>();
        } catch (IOException e) {
            HotDepositClient.LOGGER.debug("Block container state file does not exist");
            disabledBlockContainers = new HashSet<>();
        }

        // Load entity container state
        Path entityStateFile = MOD_CONFIG_DIR.resolve(fileName + "_entities.json");
        try (BufferedReader reader = Files.newBufferedReader(entityStateFile)) {
            Type type = new TypeToken<HashSet<UUID>>() {}.getType();
            disabledEntityContainers = GSON.fromJson(reader, type);
            if (disabledEntityContainers == null) disabledEntityContainers = new HashSet<>();
        } catch (IOException e) {
            HotDepositClient.LOGGER.debug("Entity container state file does not exist");
            disabledEntityContainers = new HashSet<>();
        }
    }

    private static void saveServerStateFile(ClientPlayNetworkHandler handler, MinecraftClient client) {
        if (currentStateFile == null) {
            HotDepositClient.LOGGER.debug("Current state file is null");
            return;
        }

        try {
            Files.createDirectories(MOD_CONFIG_DIR);

            // Save block container state
            String blockJson = GSON.toJson(disabledBlockContainers);
            Files.writeString(currentStateFile, blockJson);

            // Save entity container state
            Path entityStateFile = Path.of(currentStateFile.toString().replace("_blocks.json", "_entities.json"));
            String entityJson = GSON.toJson(disabledEntityContainers);
            Files.writeString(entityStateFile, entityJson);
        } catch (IOException e) {
            HotDepositClient.LOGGER.debug("Failed to save Hot Deposit state file", e);
        }
    }

    // Block container methods (using BlockPos.asLong())
    public static boolean isBlockContainerChecked(long blockPosLong) {
        return !disabledBlockContainers.contains(blockPosLong);
    }

    public static void toggleBlockContainerChecked(long blockPosLong, boolean checked) {
        if (checked)
            disabledBlockContainers.remove(blockPosLong);
        else
            disabledBlockContainers.add(blockPosLong);
    }

    // Entity container methods (using UUID)
    public static boolean isEntityContainerChecked(UUID entityUuid) {
        return !disabledEntityContainers.contains(entityUuid);
    }

    public static void toggleEntityContainerChecked(UUID entityUuid, boolean checked) {
        if (checked)
            disabledEntityContainers.remove(entityUuid);
        else
            disabledEntityContainers.add(entityUuid);
    }
}
