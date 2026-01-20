package com.example;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class LoreCommand {

    private LoreCommand() {}

    private static final int MAX_ENTRY_LENGTH = 280;

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
        LoreStorage storage = LoreStorage.get(ctx.getSource().getServer());
        long dayNumber = LoreStorage.getCurrentDay(ctx.getSource().getServer());
        List<LoreStorage.LoreEntry> entries = storage.getOrCreateNewsSnapshot(dayNumber, LoreBooks.NEWS_ENTRY_LIMIT);
        ServerPlayerEntity player = ctx.getSource().getPlayer();
        if (player != null) {
            ItemStack book = LoreBooks.createNewsBook(entries, dayNumber);
            if (!player.giveItemStack(book)) {
                ctx.getSource().sendError(Text.literal("Inventory full; couldn't deliver the news book."));
                return 0;
            }
            ctx.getSource().sendFeedback(() -> Text.literal("A news book has been delivered."), false);
        } else {
            String header = LoreBooks.formatNewsHeader(dayNumber);
            ctx.getSource().sendFeedback(() -> Text.literal(header), false);
            for (LoreStorage.LoreEntry entry : entries) {
                ctx.getSource().sendFeedback(() -> Text.literal(LoreBooks.formatEntry(entry)), false);
            }
        }

        return entries.size();
    }

}
