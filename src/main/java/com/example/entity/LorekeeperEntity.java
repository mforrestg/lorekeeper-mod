package com.example.entity;

import com.example.LoreBooks;
import com.example.LoreStorage;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;

public class LorekeeperEntity extends WanderingTraderEntity {
    private static final int NEWS_PRICE = 1;
    private static final int HISTORY_PRICE = 5;
    private static final int SUBMISSION_REWARD = 10;
    private static final int MAX_USES = 12;
    private static final float PRICE_MULTIPLIER = 0.05f;
    private static final Text NAME = Text.literal("Lore Keeper");

    public LorekeeperEntity(EntityType<? extends WanderingTraderEntity> type, World world) {
        super(type, world);
        setCustomName(NAME);
        setCustomNameVisible(true);
    }

    @Override
    protected void fillRecipes(ServerWorld world) {
        refreshOffers(world);
    }

    @Override
    public ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (getEntityWorld() instanceof ServerWorld serverWorld) {
            refreshOffers(serverWorld);
        }
        return super.interactMob(player, hand);
    }

    private void refreshOffers(ServerWorld world) {
        TradeOfferList offers = getOffers();
        offers.clear();

        LoreStorage storage = LoreStorage.get(world.getServer());
        long dayNumber = LoreStorage.getCurrentDay(world.getServer());
        List<LoreStorage.LoreEntry> snapshot = storage.getOrCreateNewsSnapshot(dayNumber, LoreBooks.NEWS_ENTRY_LIMIT);
        ItemStack newsBook = LoreBooks.createNewsBook(snapshot, dayNumber);
        ItemStack historyBook = LoreBooks.createHistoryBook(storage.getAllEntries());

        offers.add(new TradeOffer(
            new TradedItem(Items.EMERALD, NEWS_PRICE),
            Optional.empty(),
            newsBook,
            MAX_USES,
            0,
            PRICE_MULTIPLIER
        ));
        offers.add(new TradeOffer(
            new TradedItem(Items.EMERALD, HISTORY_PRICE),
            Optional.empty(),
            historyBook,
            MAX_USES,
            0,
            PRICE_MULTIPLIER
        ));
        offers.add(new TradeOffer(
            new TradedItem(Items.WRITTEN_BOOK, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD, SUBMISSION_REWARD),
            MAX_USES,
            0,
            PRICE_MULTIPLIER
        ));
    }
}
