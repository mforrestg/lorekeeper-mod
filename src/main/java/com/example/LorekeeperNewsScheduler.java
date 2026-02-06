package com.example;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

public final class LorekeeperNewsScheduler {
    private LorekeeperNewsScheduler() {}

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(LorekeeperNewsScheduler::onWorldTick);
    }

    private static void onWorldTick(ServerWorld world) {
        if (!world.getRegistryKey().equals(World.OVERWORLD)) {
            return;
        }
        LoreStorage storage = LoreStorage.get(world.getServer());
        long currentWeek = LoreStorage.getCurrentWeek(world.getServer());
        long lastAutoPublishWeek = storage.getLastAutoPublishWeek();
        if (currentWeek <= lastAutoPublishWeek) {
            return;
        }
        if (currentWeek > 0) {
            long publishWeek = currentWeek - 1;
            LorekeeperNewsPublisher.publishWeeklyNews(world.getServer(), publishWeek, true, false);
        }
        storage.setLastAutoPublishWeek(currentWeek);
    }
}
