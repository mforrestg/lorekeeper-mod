package com.example;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
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
        Uuids.LINKED_SET_CODEC.optionalFieldOf("interview_opt_out", Set.of())
            .forGetter(LorekeeperPlayerData::getInterviewOptOut),
        Codec.unboundedMap(Uuids.STRING_CODEC, Codec.INT).optionalFieldOf("question_index", Map.of())
            .forGetter(LorekeeperPlayerData::getQuestionIndexMap),
        Codec.unboundedMap(Uuids.STRING_CODEC, Codec.LONG).optionalFieldOf("last_encounter", Map.of())
            .forGetter(LorekeeperPlayerData::getLastEncounterMap),
        Codec.unboundedMap(Uuids.STRING_CODEC, Codec.STRING.listOf()).optionalFieldOf("action_flags", Map.of())
            .forGetter(LorekeeperPlayerData::getActionFlagsMap),
        Codec.unboundedMap(Uuids.STRING_CODEC, Codec.LONG.listOf()).optionalFieldOf("settlement_chunks", Map.of())
            .forGetter(LorekeeperPlayerData::getSettlementChunksMap)
    ).apply(instance, LorekeeperPlayerData::new));

    public static final PersistentStateType<LorekeeperPlayerData> STATE_TYPE = new PersistentStateType<>(
        "lorekeeper_players",
        LorekeeperPlayerData::new,
        CODEC,
        DataFixTypes.SAVED_DATA_COMMAND_STORAGE
    );

    private final Set<UUID> encountered;
    private final Set<UUID> interviewOptOut;
    private final Map<UUID, Integer> questionIndexByPlayer;
    private final Map<UUID, Long> lastEncounterByPlayer;
    private final Map<UUID, Set<String>> actionFlagsByPlayer;
    private final Map<UUID, LinkedHashSet<Long>> settlementChunksByPlayer;
    private final Map<UUID, Integer> luckByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastCombatByPlayer = new HashMap<>();
    private final Map<UUID, Long> lastChunkKeyByPlayer = new HashMap<>();
    private final Map<UUID, Long> chunkStayTicksByPlayer = new HashMap<>();

    public LorekeeperPlayerData() {
        this(Set.of(), Set.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public LorekeeperPlayerData(
        Set<UUID> encountered,
        Set<UUID> interviewOptOut,
        Map<UUID, Integer> questionIndexByPlayer,
        Map<UUID, Long> lastEncounterByPlayer,
        Map<UUID, List<String>> actionFlagsByPlayer,
        Map<UUID, List<Long>> settlementChunksByPlayer
    ) {
        this.encountered = new HashSet<>(encountered);
        this.interviewOptOut = new HashSet<>(interviewOptOut);
        this.questionIndexByPlayer = new HashMap<>(questionIndexByPlayer);
        this.lastEncounterByPlayer = new HashMap<>(lastEncounterByPlayer);
        this.actionFlagsByPlayer = new HashMap<>();
        for (Map.Entry<UUID, List<String>> entry : actionFlagsByPlayer.entrySet()) {
            this.actionFlagsByPlayer.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        this.settlementChunksByPlayer = new HashMap<>();
        for (Map.Entry<UUID, List<Long>> entry : settlementChunksByPlayer.entrySet()) {
            this.settlementChunksByPlayer.put(entry.getKey(), new LinkedHashSet<>(entry.getValue()));
        }
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

    public boolean isInterviewOptedOut(UUID playerId) {
        return interviewOptOut.contains(playerId);
    }

    public void setInterviewOptOut(UUID playerId, boolean optedOut) {
        boolean changed = optedOut ? interviewOptOut.add(playerId) : interviewOptOut.remove(playerId);
        if (changed) {
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

    public boolean isOnEncounterCooldown(UUID playerId, long nowTicks, long cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastEncounterByPlayer.get(playerId);
        return last != null && nowTicks - last < cooldownTicks;
    }

    public void markEncounterTime(UUID playerId, long nowTicks) {
        lastEncounterByPlayer.put(playerId, nowTicks);
        markDirty();
    }

    public boolean markActionFlag(UUID playerId, String flag) {
        Set<String> flags = actionFlagsByPlayer.computeIfAbsent(playerId, ignored -> new HashSet<>());
        if (flags.add(flag)) {
            markDirty();
            return true;
        }
        return false;
    }

    public boolean hasActionFlag(UUID playerId, String flag) {
        Set<String> flags = actionFlagsByPlayer.get(playerId);
        return flags != null && flags.contains(flag);
    }

    public boolean registerSettlementChunk(UUID playerId, long chunkKey, int maxEntries) {
        LinkedHashSet<Long> chunks =
            settlementChunksByPlayer.computeIfAbsent(playerId, ignored -> new LinkedHashSet<>());
        if (chunks.contains(chunkKey)) {
            return false;
        }
        chunks.add(chunkKey);
        if (chunks.size() > maxEntries) {
            Long oldest = chunks.iterator().next();
            chunks.remove(oldest);
        }
        markDirty();
        return true;
    }

    public int getLuck(UUID playerId) {
        return luckByPlayer.getOrDefault(playerId, 0);
    }

    public void incrementLuck(UUID playerId) {
        luckByPlayer.put(playerId, getLuck(playerId) + 1);
    }

    public void resetLuck(UUID playerId) {
        luckByPlayer.remove(playerId);
    }

    public void markCombat(UUID playerId, long nowTicks) {
        lastCombatByPlayer.put(playerId, nowTicks);
    }

    public boolean isInCombat(UUID playerId, long nowTicks, long cooldownTicks) {
        if (cooldownTicks <= 0) {
            return false;
        }
        Long last = lastCombatByPlayer.get(playerId);
        return last != null && nowTicks - last < cooldownTicks;
    }

    public long updateChunkStay(UUID playerId, long chunkKey) {
        Long lastKey = lastChunkKeyByPlayer.get(playerId);
        if (lastKey == null || lastKey != chunkKey) {
            lastChunkKeyByPlayer.put(playerId, chunkKey);
            chunkStayTicksByPlayer.put(playerId, 0L);
            return 0L;
        }
        long next = chunkStayTicksByPlayer.getOrDefault(playerId, 0L) + 1L;
        chunkStayTicksByPlayer.put(playerId, next);
        return next;
    }

    public void resetChunkStay(UUID playerId) {
        chunkStayTicksByPlayer.put(playerId, 0L);
    }

    private Set<UUID> getEncountered() {
        return encountered;
    }

    private Set<UUID> getInterviewOptOut() {
        return interviewOptOut;
    }

    private Map<UUID, Integer> getQuestionIndexMap() {
        return questionIndexByPlayer;
    }

    private Map<UUID, Long> getLastEncounterMap() {
        return lastEncounterByPlayer;
    }

    private Map<UUID, List<String>> getActionFlagsMap() {
        Map<UUID, List<String>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, Set<String>> entry : actionFlagsByPlayer.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }

    private Map<UUID, List<Long>> getSettlementChunksMap() {
        Map<UUID, List<Long>> snapshot = new HashMap<>();
        for (Map.Entry<UUID, LinkedHashSet<Long>> entry : settlementChunksByPlayer.entrySet()) {
            snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        return snapshot;
    }
}
