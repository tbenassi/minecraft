package io.github.tbenassi.com.chunkloader;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.ChunkPos;
import org.slf4j.Logger;

import java.util.Map;
import java.util.Set;

public class ChunkLoaderMod implements ModInitializer {
    public static final String MOD_ID = "chunk-loader";
    public static final Logger LOGGER = LogUtils.getLogger();

    @Override
    public void onInitialize() {
        CommandRegistrationCallback.EVENT.register(ChunkLoaderCommands::register);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (server.getPlayerManager().getPlayerList().size() == 1) {
                forceAllChunks(server);
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (server.getPlayerManager().getPlayerList().size() == 1) {
                unforceAllChunks(server);
            }
        });

        ServerLifecycleEvents.SERVER_STOPPING.register(ChunkLoaderMod::unforceAllChunks);

        LOGGER.info("Chunk Loader mod initialized");
    }

    public static void forceAllChunks(MinecraftServer server) {
        ChunkLoaderState state = ChunkLoaderState.get(server);
        int count = 0;
        for (Map.Entry<String, Set<ChunkPos>> entry : state.getAllChunks().entrySet()) {
            ServerWorld world = getWorldByDimensionId(server, entry.getKey());
            if (world != null) {
                for (ChunkPos pos : entry.getValue()) {
                    world.setChunkForced(pos.x, pos.z, true);
                    count++;
                }
            }
        }
        if (count > 0) {
            LOGGER.info("Force-loaded {} chunk(s)", count);
        }
    }

    public static void unforceAllChunks(MinecraftServer server) {
        ChunkLoaderState state = ChunkLoaderState.get(server);
        int count = 0;
        for (Map.Entry<String, Set<ChunkPos>> entry : state.getAllChunks().entrySet()) {
            ServerWorld world = getWorldByDimensionId(server, entry.getKey());
            if (world != null) {
                for (ChunkPos pos : entry.getValue()) {
                    world.setChunkForced(pos.x, pos.z, false);
                    count++;
                }
            }
        }
        if (count > 0) {
            LOGGER.info("Unforced {} chunk(s)", count);
        }
    }

    private static ServerWorld getWorldByDimensionId(MinecraftServer server, String dimensionId) {
        for (ServerWorld world : server.getWorlds()) {
            if (world.getRegistryKey().getValue().toString().equals(dimensionId)) {
                return world;
            }
        }
        return null;
    }
}
