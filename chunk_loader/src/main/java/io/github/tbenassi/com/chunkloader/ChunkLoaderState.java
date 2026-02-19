package io.github.tbenassi.com.chunkloader;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

import java.util.*;

public class ChunkLoaderState extends PersistentState {

    public record ChunkEntry(String dimension, int chunkX, int chunkZ) {
        public static final Codec<ChunkEntry> CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                Codec.STRING.fieldOf("dimension").forGetter(ChunkEntry::dimension),
                Codec.INT.fieldOf("x").forGetter(ChunkEntry::chunkX),
                Codec.INT.fieldOf("z").forGetter(ChunkEntry::chunkZ)
            ).apply(instance, ChunkEntry::new)
        );
    }

    public static final Codec<ChunkLoaderState> CODEC = RecordCodecBuilder.create(instance ->
        instance.group(
            ChunkEntry.CODEC.listOf()
                .optionalFieldOf("chunks", List.of())
                .forGetter(ChunkLoaderState::toEntryList)
        ).apply(instance, ChunkLoaderState::new)
    );

    public static final PersistentStateType<ChunkLoaderState> STATE_TYPE = new PersistentStateType<>(
        "chunk_loader", ChunkLoaderState::new, CODEC, DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Map<String, Set<ChunkPos>> chunks = new HashMap<>();

    public ChunkLoaderState() {
    }

    private ChunkLoaderState(List<ChunkEntry> entries) {
        for (ChunkEntry entry : entries) {
            chunks.computeIfAbsent(entry.dimension(), k -> new HashSet<>())
                .add(new ChunkPos(entry.chunkX(), entry.chunkZ()));
        }
    }

    public boolean addChunk(String dimension, ChunkPos pos) {
        boolean added = chunks.computeIfAbsent(dimension, k -> new HashSet<>()).add(pos);
        if (added) {
            markDirty();
        }
        return added;
    }

    public boolean removeChunk(String dimension, ChunkPos pos) {
        Set<ChunkPos> set = chunks.get(dimension);
        if (set == null) {
            return false;
        }
        boolean removed = set.remove(pos);
        if (removed) {
            if (set.isEmpty()) {
                chunks.remove(dimension);
            }
            markDirty();
        }
        return removed;
    }

    public boolean hasChunk(String dimension, ChunkPos pos) {
        Set<ChunkPos> set = chunks.get(dimension);
        return set != null && set.contains(pos);
    }

    public Map<String, Set<ChunkPos>> getAllChunks() {
        return Collections.unmodifiableMap(chunks);
    }

    public int getTotalCount() {
        int count = 0;
        for (Set<ChunkPos> set : chunks.values()) {
            count += set.size();
        }
        return count;
    }

    public void clearAll() {
        if (!chunks.isEmpty()) {
            chunks.clear();
            markDirty();
        }
    }

    private List<ChunkEntry> toEntryList() {
        List<ChunkEntry> entries = new ArrayList<>();
        for (var entry : chunks.entrySet()) {
            for (ChunkPos pos : entry.getValue()) {
                entries.add(new ChunkEntry(entry.getKey(), pos.x, pos.z));
            }
        }
        return entries;
    }

    public static ChunkLoaderState get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }
}
