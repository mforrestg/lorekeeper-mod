package com.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Uuids;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateType;

public final class LorekeeperPlayerData extends PersistentState {
    private static final Codec<LorekeeperPlayerData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
        Uuids.LINKED_SET_CODEC.optionalFieldOf("encountered", Set.of())
            .forGetter(LorekeeperPlayerData::getEncountered),
        Codec.unboundedMap(Uuids.STRING_CODEC, Codec.INT).optionalFieldOf("question_index", Map.of())
            .forGetter(LorekeeperPlayerData::getQuestionIndexMap)
    ).apply(instance, LorekeeperPlayerData::new));

    public static final PersistentStateType<LorekeeperPlayerData> STATE_TYPE = new PersistentStateType<>(
        "lorekeeper_players",
        LorekeeperPlayerData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<UUID> encountered;
    private final Map<UUID, Integer> questionIndexByPlayer;

    public LorekeeperPlayerData() {
        this(Set.of(), Map.of());
    }

    public LorekeeperPlayerData(Set<UUID> encountered, Map<UUID, Integer> questionIndexByPlayer) {
        this.encountered = new HashSet<>(encountered);
        this.questionIndexByPlayer = new HashMap<>(questionIndexByPlayer);
    }

    public static LorekeeperPlayerData get(MinecraftServer server) {
        return server.getOverworld().getPersistentStateManager().getOrCreate(STATE_TYPE);
    }

    public boolean hasEncountered(UUID playerId) {
        return encountered.contains(playerId);
    }

    public void markEncountered(UUID playerId) {
        if (encountered.add(playerId)) {
            markDirty();
        }
    }

    public int nextQuestionStart(UUID playerId, int poolSize, int questionsPerInterview) {
        if (poolSize <= 0) {
            return 0;
        }
        int start = questionIndexByPlayer.getOrDefault(playerId, 0);
        int next = (start + questionsPerInterview) % poolSize;
        questionIndexByPlayer.put(playerId, next);
        markDirty();
        return start;
    }

    private Set<UUID> getEncountered() {
        return encountered;
    }

    private Map<UUID, Integer> getQuestionIndexMap() {
        return questionIndexByPlayer;
    }
}
