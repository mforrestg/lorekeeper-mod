package com.example;

import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

public final class LorekeeperMilestones {
    private LorekeeperMilestones() {}

    public static void record(ServerPlayerEntity player, String text, String eventType, List<String> tags) {
        record(player, text, eventType, tags, player.getBlockPos());
    }

    public static void record(
        ServerPlayerEntity player,
        String text,
        String eventType,
        List<String> tags,
        BlockPos pos
    ) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        if (!(player.getEntityWorld() instanceof ServerWorld world)) {
            return;
        }
        LoreStorage storage = LoreStorage.get(world.getServer());
        LoreStorage.LoreEntry entry = LoreStorage.buildEntry(
            text.trim(),
            player.getName().getString(),
            System.currentTimeMillis(),
            world,
            pos,
            eventType,
            tags
        );
        storage.addEntry(entry);
    }
}
