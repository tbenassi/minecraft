package io.github.tbenassi.com.chunkloader;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.Set;

public class ChunkLoaderCommands {

    public static void register(
        CommandDispatcher<ServerCommandSource> dispatcher,
        CommandRegistryAccess registryAccess,
        CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(
            CommandManager.literal("chunkloader")
                .requires(CommandManager.requirePermissionLevel(CommandManager.GAMEMASTERS_CHECK))
                .then(CommandManager.literal("add").executes(ChunkLoaderCommands::executeAdd))
                .then(CommandManager.literal("remove").executes(ChunkLoaderCommands::executeRemove))
                .then(CommandManager.literal("list").executes(ChunkLoaderCommands::executeList))
                .then(CommandManager.literal("clear").executes(ChunkLoaderCommands::executeClear))
        );
    }

    private static int executeAdd(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        ServerWorld world = player.getEntityWorld();
        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();

        ChunkLoaderState state = ChunkLoaderState.get(source.getServer());
        if (!state.addChunk(dimension, chunkPos)) {
            source.sendFeedback(() -> Text.literal("Chunk [" + chunkPos.x + ", " + chunkPos.z + "] in " + dimension + " is already marked for loading."), false);
            return 0;
        }

        // Immediately force the chunk since at least one player is online (the one running the command)
        world.setChunkForced(chunkPos.x, chunkPos.z, true);
        source.sendFeedback(() -> Text.literal("Chunk [" + chunkPos.x + ", " + chunkPos.z + "] in " + dimension + " is now marked for loading."), true);
        return 1;
    }

    private static int executeRemove(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ServerPlayerEntity player = source.getPlayer();
        if (player == null) {
            source.sendError(Text.literal("This command must be run by a player."));
            return 0;
        }

        ServerWorld world = player.getEntityWorld();
        String dimension = world.getRegistryKey().getValue().toString();
        ChunkPos chunkPos = player.getChunkPos();

        ChunkLoaderState state = ChunkLoaderState.get(source.getServer());
        if (!state.removeChunk(dimension, chunkPos)) {
            source.sendFeedback(() -> Text.literal("Chunk [" + chunkPos.x + ", " + chunkPos.z + "] in " + dimension + " is not marked for loading."), false);
            return 0;
        }

        world.setChunkForced(chunkPos.x, chunkPos.z, false);
        source.sendFeedback(() -> Text.literal("Chunk [" + chunkPos.x + ", " + chunkPos.z + "] in " + dimension + " is no longer marked for loading."), true);
        return 1;
    }

    private static int executeList(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ChunkLoaderState state = ChunkLoaderState.get(source.getServer());
        Map<String, Set<ChunkPos>> allChunks = state.getAllChunks();

        if (allChunks.isEmpty()) {
            source.sendFeedback(() -> Text.literal("No chunks are marked for loading."), false);
            return 0;
        }

        int total = state.getTotalCount();
        source.sendFeedback(() -> Text.literal("Marked chunks (" + total + " total):"), false);
        for (var entry : allChunks.entrySet()) {
            String dim = entry.getKey();
            for (ChunkPos pos : entry.getValue()) {
                source.sendFeedback(() -> Text.literal("  " + dim + ": [" + pos.x + ", " + pos.z + "]"), false);
            }
        }
        return total;
    }

    private static int executeClear(CommandContext<ServerCommandSource> context) {
        ServerCommandSource source = context.getSource();
        ChunkLoaderState state = ChunkLoaderState.get(source.getServer());
        int count = state.getTotalCount();

        if (count == 0) {
            source.sendFeedback(() -> Text.literal("No chunks are marked for loading."), false);
            return 0;
        }

        // Unforce all chunks first
        ChunkLoaderMod.unforceAllChunks(source.getServer());
        state.clearAll();

        source.sendFeedback(() -> Text.literal("Cleared " + count + " marked chunk(s)."), true);
        return count;
    }
}
