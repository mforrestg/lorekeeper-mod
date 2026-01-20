package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.command.permission.Permission;
import net.minecraft.command.permission.PermissionLevel;
import java.util.List;
import net.minecraft.server.command.CommandManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class LoreCommand {

    private LoreCommand() {}

    private static final int MAX_ENTRY_LENGTH = 280;
    private static final int DEFAULT_LIST_COUNT = 10;
    private static final int MAX_LIST_COUNT = 100;

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(
                CommandManager.literal("lore")
                    .then(CommandManager.literal("log")
                        .then(CommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(LoreCommand::logLore)
                        )
                    )
                    .then(CommandManager.literal("news")
                        .executes(LoreCommand::sendNews)
                    )
                    .then(CommandManager.literal("interview")
                        .then(CommandManager.literal("accept")
                            .executes(LoreCommand::acceptInterview)
                        )
                        .then(CommandManager.literal("decline")
                            .executes(LoreCommand::declineInterview)
                        )
                    )
                    .then(CommandManager.literal("admin")
                        .requires(LoreCommand::hasAdminPermission)
                        .then(CommandManager.literal("list")
                            .executes(LoreCommand::listLore)
                            .then(CommandManager.argument("count", IntegerArgumentType.integer(1, MAX_LIST_COUNT))
                                .executes(LoreCommand::listLoreWithCount)
                            )
                        )
                        .then(CommandManager.literal("delete")
                            .then(CommandManager.argument("id", IntegerArgumentType.integer(1))
                                .executes(LoreCommand::deleteLore)
                            )
                        )
                        .then(CommandManager.literal("clear")
                            .executes(LoreCommand::promptClearLore)
                            .then(CommandManager.literal("confirm")
                                .executes(LoreCommand::clearLore)
                            )
                        )
                        .then(CommandManager.literal("news")
                            .then(CommandManager.literal("publish")
                                .executes(LoreCommand::publishNews)
                            )
                            .then(CommandManager.literal("preview")
                                .executes(LoreCommand::previewNews)
                            )
                        )
                        .then(CommandManager.literal("reload")
                            .executes(LoreCommand::reloadConfig)
                        )
                    )
            );
        });
    }

    private static int logLore(CommandContext<ServerCommandSource> ctx) {
        String text = StringArgumentType.getString(ctx, "text").trim();
        if (text.isEmpty()) {
            ctx.getSource().sendError(Text.literal("Lore cannot be empty."));
            return 0;
        }
        if (text.length() > MAX_ENTRY_LENGTH) {
            ctx.getSource().sendError(Text.literal("Lore is too long (max " + MAX_ENTRY_LENGTH + " chars)."));
            return 0;
        }

        String author = ctx.getSource().getName();
        long timestamp = System.currentTimeMillis();
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        storage.addEntryText(text, author, timestamp);

        LorekeeperMod.LOGGER.info("[LORE] {} - {}", author, text);
        ctx.getSource().sendFeedback(() -> Text.literal("Lore recorded."), false);
        return 1;
    }

    private static int sendNews(CommandContext<ServerCommandSource> ctx) {
        LorekeeperNewsPublisher.PublishResult result =
            LorekeeperNewsPublisher.publishWeeklyNews(ctx.getSource().getServer(), true);
        long weekNumber = result.weekNumber();
        List<LoreStorage.LoreEntry> entries = result.entries();
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            ItemStack book = LoreBooks.createNewsBook(entries, weekNumber);
            if (!player.giveItemStack(book)) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the news book."));
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal("A news book has been delivered."), false);
        } else {
            String header = LoreBooks.formatNewsHeader(weekNumber);
            ctx.getSource().sendFeedback(() -> Text.literal(header), false);
            for (LoreStorage.LoreEntry entry : entries) {
                ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
            }
        }

        return entries.size();
    }

    private static int acceptInterview(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can accept interviews."));
            return 0;
        }
        return LorekeeperInterviewManager.accept(player) ? 1 : 0;
    }

    private static int declineInterview(CommandContext<ServerCommandSource> ctx) {
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendError(Text.literal("Only players can decline interviews."));
            return 0;
        }
        LorekeeperInterviewManager.decline(player);
        return 1;
    }

    private static boolean hasAdminPermission(ServerCommandSource source) {
        return source.getPermissions().hasPermission(new Permission.Level(PermissionLevel.fromLevel(2)));
    }

    private static int listLore(CommandContext<ServerCommandSource> ctx) {
        return listLore(ctx, DEFAULT_LIST_COUNT);
    }

    private static int listLoreWithCount(CommandContext<ServerCommandSource> ctx) {
        int count = IntegerArgumentType.getInteger(ctx, "count");
        return listLore(ctx, count);
    }

    private static int listLore(CommandContext<ServerCommandSource> ctx, int count) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getAllEntries();
        if (entries.isEmpty()) {
            ctx.getSource().sendFeedback(() -> Text.literal("No lore entries recorded."), false);
            return 0;
        }
        int total = entries.size();
        int start = Math.max(total - count, 0);
        ctx.getSource().sendFeedback(
            () -> Text.literal("Showing " + (total - start) + " of " + total + " lore entries (newest first)."),
            false
        );
        for (int i = total - 1; i >= start; i--) {
            int id = i + 1;
            String line = "#" + id + " " + LoreBooks.formatEntry(entries.get(i)).replace('\n', ' ');
            ctx.getSource().sendFeedback(() -> Text.literal(line), false);
        }
        return total - start;
    }

    private static int deleteLore(CommandContext<ServerCommandSource> ctx) {
        int id = IntegerArgumentType.getInteger(ctx, "id");
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        int index = id - 1;
        LoreStorage.LoreEntry removed = storage.removeEntryAt(index);
        if (removed == null) {
            ctx.getSource().sendError(Text.literal("Lore id out of range (1-" + storage.getEntryCount() + ")."));
            return 0;
        }
        ctx.getSource().sendFeedback(() -> Text.literal("Deleted lore #" + id + "."), false);
        return 1;
    }

    private static int promptClearLore(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendError(Text.literal("This will delete all lore. Run /lore admin clear confirm to proceed."));
        return 0;
    }

    private static int clearLore(CommandContext<ServerCommandSource> ctx) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        int count = storage.getEntryCount();
        storage.clearAll();
        ctx.getSource().sendFeedback(
            () -> Text.literal("Cleared " + count + " lore entries and all weekly news snapshots."),
            false
        );
        return count;
    }

    private static int publishNews(CommandContext<ServerCommandSource> ctx) {
        LorekeeperNewsPublisher.PublishResult result =
            LorekeeperNewsPublisher.publishWeeklyNews(ctx.getSource().getServer(), true);
        if (result.alreadyPublished()) {
            ctx.getSource().sendFeedback(
                () -> Text.literal("News for week " + result.weekNumber() + " was already published."),
                false
            );
        } else {
            ctx.getSource().sendFeedback(
                () -> Text.literal("Published news for week " + result.weekNumber() + "."),
                false
            );
        }
        return result.entries().size();
    }

    private static int previewNews(CommandContext<ServerCommandSource> ctx) {
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        long weekNumber = LoreStorage.getCurrentWeek(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getNewsSnapshot(weekNumber);
        boolean published = entries != null;
        if (entries == null) {
            entries = storage.getLatestEntries(LoreBooks.NEWS_ENTRY_LIMIT);
        }

        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            ItemStack book = LoreBooks.createNewsBook(entries, weekNumber);
            if (!player.giveItemStack(book)) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the preview book."));
                return 0;
            }
            String label = published ? "Published news preview delivered." : "Preview book delivered (not yet published).";
            ctx.getSource().sendFeedback(() -> Text.literal(label), false);
        } else {
            String header = LoreBooks.formatNewsHeader(weekNumber);
            String suffix = published ? " (published)" : " (preview)";
            ctx.getSource().sendFeedback(() -> Text.literal(header + suffix), false);
            for (LoreStorage.LoreEntry entry : entries) {
                ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
            }
        }

        return entries.size();
    }

    private static int reloadConfig(CommandContext<ServerCommandSource> ctx) {
        LorekeeperMod.CONFIG = LorekeeperConfig.load();
        ctx.getSource().sendFeedback(() -> Text.literal("Lorekeeper config reloaded."), false);
        return 1;
    }
}
