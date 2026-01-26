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

public class ClientState {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final String MOD_ID = "hot-deposit";
    public static final Path MOD_CONFIG_DIR = FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    @Nullable
    private static Path currentStateFile;
    private static HashSet<Long> currentState = new HashSet<>();

    public static void init() {
        ClientPlayConnectionEvents.JOIN.register(ClientState::initServerStateFile);
        ClientPlayConnectionEvents.DISCONNECT.register(ClientState::saveServerStateFile);
    }

    private static void initServerStateFile(ClientPlayNetworkHandler handler, PacketSender sender, MinecraftClient client) {
        IntegratedServer integratedServer = client.getServer();
        ServerInfo currentServerEntry = client.getCurrentServerEntry();
        String fileName;
        if (integratedServer != null) {
            fileName = ((MinecraftServerAccessor) integratedServer).getSession().getDirectoryName().concat(".json");
        } else if (currentServerEntry != null){
            fileName = currentServerEntry.address.concat(".json").replace(":", "colon");
        } else {
            HotDepositClient.LOGGER.info("Failed to get server or level name");
            return;
        }
        currentStateFile = MOD_CONFIG_DIR.resolve(fileName);

        try (BufferedReader reader = Files.newBufferedReader(currentStateFile)) {
            Type type = new TypeToken<HashSet<Long>>() {}.getType();
            currentState = GSON.fromJson(reader, type);
        } catch (IOException e) {
            HotDepositClient.LOGGER.info("Hot Deposit state file does not exist", e);
        }
    }

    private static void saveServerStateFile(ClientPlayNetworkHandler handler, MinecraftClient client) {
        if (currentStateFile == null) {
            HotDepositClient.LOGGER.info("Current state file is null");
            return;
        }

        try {
            Files.createDirectories(MOD_CONFIG_DIR);
            String json = GSON.toJson(currentState);
            Files.writeString(currentStateFile, json);
        } catch (IOException e) {
            HotDepositClient.LOGGER.info("Failed to save Hot Deposit state file", e);
        }
    }

    public static boolean isContainerChecked(Long containerId) {
        return !currentState.contains(containerId);
    }

    public static void toggleContainerChecked(Long containerId, boolean checked) {
        if (checked)
            currentState.remove(containerId);
        else
            currentState.add(containerId);
    }
}
