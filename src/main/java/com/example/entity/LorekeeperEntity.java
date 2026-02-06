package com.example.entity;

import com.example.LoreBooks;
import com.example.LoreStorage;
import com.example.LorekeeperAiService;
import com.example.LorekeeperConfig;
import com.example.LorekeeperMod;
import com.example.LorekeeperNewsPublisher;
import java.util.List;
import java.util.Optional;
import net.minecraft.entity.ai.goal.HoldInHandsGoal;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.WanderingTraderEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.village.TradeOffer;
import net.minecraft.village.TradeOfferList;
import net.minecraft.village.TradedItem;
import net.minecraft.world.World;

public class LorekeeperEntity extends WanderingTraderEntity {
    private static final int DEFAULT_NEWS_PRICE = 1;
    private static final int DEFAULT_HISTORY_PRICE = 5;
    private static final int DEFAULT_SUBMISSION_REWARD = 10;
    private static final int DEFAULT_MAX_USES = 12;
    private static final float DEFAULT_PRICE_MULTIPLIER = 0.05f;
    private static final Text NAME = Text.literal("Lore Keeper");
    private static final double DEFAULT_MAX_WANDER_DISTANCE = 12.0;
    private static final double DEFAULT_RETURN_SPEED = 0.4;
    private static final double DEFAULT_BASE_SPEED = 0.35;

    private BlockPos anchorPos;

    public LorekeeperEntity(EntityType<? extends WanderingTraderEntity> type, World world) {
        super(type, world);
        setCustomName(NAME);
        setCustomNameVisible(true);
    }

    @Override
    protected void initGoals() {
        super.initGoals();
        goalSelector.clear(goal -> goal instanceof HoldInHandsGoal);
    }

    @Override
    public void tickMovement() {
        super.tickMovement();
        if (getEntityWorld().isClient() || anchorPos == null) {
            return;
        }
        Vec3d anchorCenter = Vec3d.ofCenter(anchorPos);
        double maxDistance = getMaxWanderDistance();
        if (squaredDistanceTo(anchorCenter) > maxDistance * maxDistance) {
            getNavigation().startMovingTo(anchorCenter.x, anchorCenter.y, anchorCenter.z, getReturnSpeed());
        }
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
        LorekeeperNewsPublisher.PublishResult result =
            LorekeeperNewsPublisher.publishWeeklyNews(world.getServer(), true, false);
        LoreBooks.BookResult newsResult =
            LoreBooks.createNewsBookResult(result.entries(), result.weekNumber(), result.aiSummary());
        if (newsResult.truncated()) {
            LorekeeperMod.LOGGER.info("Lorekeeper news book truncated to {} pages.", newsResult.maxPages());
        }
        String historySummary =
            LorekeeperAiService.getOrCreateHistorySummaryNonBlocking(world.getServer(), storage.getAllEntries());
        LoreBooks.BookResult historyResult =
            LoreBooks.createHistoryBookResult(storage.getAllEntries(), historySummary);
        if (historyResult.truncated()) {
            LorekeeperMod.LOGGER.info("Lorekeeper archive book truncated to {} pages.", historyResult.maxPages());
        }

        offers.add(new TradeOffer(
            new TradedItem(Items.EMERALD, getNewsPrice()),
            Optional.empty(),
            newsResult.book(),
            getMaxUses(),
            0,
            getPriceMultiplier()
        ));
        offers.add(new TradeOffer(
            new TradedItem(Items.EMERALD, getHistoryPrice()),
            Optional.empty(),
            historyResult.book(),
            getMaxUses(),
            0,
            getPriceMultiplier()
        ));
        offers.add(new TradeOffer(
            new TradedItem(Items.WRITTEN_BOOK, 1),
            Optional.empty(),
            new ItemStack(Items.EMERALD, getSubmissionReward()),
            getMaxUses(),
            0,
            getPriceMultiplier()
        ));
    }

    public void setAnchorPos(BlockPos anchorPos) {
        this.anchorPos = anchorPos;
        setWanderTarget(anchorPos);
        EntityAttributeInstance speed = getAttributeInstance(EntityAttributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(getBaseSpeed());
        }
    }

    private static int getNewsPrice() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.newsPrice : DEFAULT_NEWS_PRICE;
    }

    private static int getHistoryPrice() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.historyPrice : DEFAULT_HISTORY_PRICE;
    }

    private static int getSubmissionReward() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.submissionReward : DEFAULT_SUBMISSION_REWARD;
    }

    private static int getMaxUses() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.tradeMaxUses : DEFAULT_MAX_USES;
    }

    private static float getPriceMultiplier() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.tradePriceMultiplier : DEFAULT_PRICE_MULTIPLIER;
    }

    private static double getMaxWanderDistance() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.lorekeeperMaxWanderDistance : DEFAULT_MAX_WANDER_DISTANCE;
    }

    private static double getReturnSpeed() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.lorekeeperReturnSpeed : DEFAULT_RETURN_SPEED;
    }

    private static double getBaseSpeed() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        return config != null ? config.lorekeeperBaseSpeed : DEFAULT_BASE_SPEED;
    }
}
