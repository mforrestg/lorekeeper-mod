package com.example;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

public final class LorekeeperInterviewManager {
    private static final int QUESTIONS_PER_INTERVIEW = 3;
    private static final List<String> BASIC_QUESTIONS = List.of(
        "What should the archive call you?",
        "Where did your journey begin?",
        "What brought you to this land?",
        "What are you hoping to build or discover first?",
        "Do you have any companions or a group you travel with?",
        "What place feels safest to you right now?",
        "What tools or resources are you seeking today?",
        "What would you like others to know about you?",
        "What goal are you most excited about?",
        "What should the archives remember about your first day?"
    );
    private static final List<String> MAIN_QUESTIONS = List.of(
        "What brought you to this part of the world?",
        "What is the main project you are working on today?",
        "What problem are you trying to solve right now?",
        "Who has helped you recently, and how?",
        "What did you build or discover that others should know about?",
        "What danger or challenge are you preparing for?",
        "What place on the map matters most to you this week, and why?",
        "What did you lose or sacrifice to get where you are now?",
        "What was the most surprising thing you saw today?",
        "What rumor should the archives keep an eye on?",
        "Who is your closest ally, and what do they want?",
        "Who is your main rival, and what is the conflict about?",
        "What resource or item has become most important recently?",
        "What agreement or deal have you made lately?",
        "What would you want future players to remember about today?",
        "If you could change one thing about the world, what would it be?",
        "What is the next big goal you are working toward?",
        "What place feels safest to you, and why?",
        "What event from today felt historic?",
        "What question should the Lore Keeper ask next time?"
    );
    private static final Map<UUID, QuestionPool> PENDING = new HashMap<>();
    private static final Map<UUID, InterviewSession> ACTIVE = new HashMap<>();
    private static final List<String> FALLBACK_REACTIONS = List.of(
        "Noted. The archive grows richer.",
        "Thank you. The record will remember this.",
        "Interesting. I will keep it in the ledger.",
        "A valuable detail. The story deepens.",
        "So it shall be recorded."
    );

    private LorekeeperInterviewManager() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(LorekeeperInterviewManager::onServerTick);
    }

    public static void offerInterview(ServerPlayerEntity player) {
        offerInterview(player, Text.literal("Would you be willing to conduct another interview? "), QuestionPool.MAIN);
    }

    public static void offerInterview(ServerPlayerEntity player, Text prompt) {
        offerInterview(player, prompt, QuestionPool.MAIN);
    }

    public static void offerInterview(ServerPlayerEntity player, Text prompt, QuestionPool pool) {
        if (isOptedOut(player)) {
            return;
        }
        if (ACTIVE.containsKey(player.getUuid())) {
            sendLorekeeperMessage(player, Text.literal("We are already in an interview."));
            return;
        }
        PENDING.put(player.getUuid(), pool);
        MutableText accept = Text.literal("[Accept]")
            .setStyle(Style.EMPTY.withColor(Formatting.GREEN)
                .withClickEvent(new ClickEvent.RunCommand("/lore interview accept")));
        MutableText decline = Text.literal("[Decline]")
            .setStyle(Style.EMPTY.withColor(Formatting.RED)
                .withClickEvent(new ClickEvent.RunCommand("/lore interview decline")));
        sendLorekeeperMessage(player,
            prompt.copy().append(accept).append(Text.literal(" ")).append(decline));
    }

    public static boolean accept(ServerPlayerEntity player) {
        QuestionPool pool = PENDING.remove(player.getUuid());
        if (pool == null) {
            sendLorekeeperMessage(player, Text.literal("There is no interview pending right now."));
            return false;
        }
        startInterview(player, pool);
        return true;
    }

    public static void decline(ServerPlayerEntity player) {
        if (PENDING.remove(player.getUuid()) != null) {
            sendLorekeeperMessage(player, Text.literal("Very well. Perhaps another time."));
        } else {
            sendLorekeeperMessage(player, Text.literal("There is no interview pending right now."));
        }
    }

    public static void startInterview(ServerPlayerEntity player) {
        startInterview(player, QuestionPool.MAIN);
    }

    public static void startInterview(ServerPlayerEntity player, QuestionPool pool) {
        if (isOptedOut(player)) {
            sendLorekeeperMessage(player, Text.literal("Very well. I will not press you for an interview."));
            return;
        }
        if (ACTIVE.containsKey(player.getUuid())) {
            sendLorekeeperMessage(player, Text.literal("We are already in an interview."));
            return;
        }
        sendLorekeeperMessage(player, Text.literal("I have a few questions for the archive."));
        List<String> questions = pickQuestions(player, pool);
        InterviewSession session = new InterviewSession(questions);
        session.touch();
        ACTIVE.put(player.getUuid(), session);
        askNextQuestion(player);
    }

    public static boolean handleChatAnswer(ServerPlayerEntity player, String message) {
        InterviewSession session = ACTIVE.get(player.getUuid());
        if (session == null) {
            return false;
        }
        if (session.awaitingReaction) {
            return false;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        session.touch();
        String question = session.currentQuestion();
        if (question == null) {
            endInterview(player);
            return false;
        }

        ServerWorld world = (ServerWorld) player.getEntityWorld();
        LoreStorage storage = LoreStorage.get(world.getServer());
        String entry = "Interview answer to \"" + question + "\": " + trimmed;
        storage.addEntryTextChunked(
            entry,
            player.getName().getString(),
            System.currentTimeMillis(),
            500,
            world.getRegistryKey().getValue().toString(),
            player.getBlockPos().getX(),
            player.getBlockPos().getY(),
            player.getBlockPos().getZ(),
            "interview",
            List.of("interview", "answer")
        );

        session.advance();
        session.awaitingReaction = true;

        CompletableFuture
            .supplyAsync(() -> LorekeeperAiService.generateInterviewReaction(player.getName().getString(), question, trimmed))
            .thenAccept(reaction -> world.getServer().execute(() -> {
                InterviewSession current = ACTIVE.get(player.getUuid());
                if (current == null) {
                    return;
                }
                String line = reaction;
                if (line == null || line.isBlank()) {
                    line = pickFallbackReaction(player);
                }
                sendLorekeeperMessage(player, Text.literal(line));
                current.awaitingReaction = false;
                current.touch();
                if (!askNextQuestion(player)) {
                    sendLorekeeperMessage(player, Text.literal("Our interview is complete."));
                    endInterview(player);
                }
            }));
        return true;
    }

    private static boolean askNextQuestion(ServerPlayerEntity player) {
        InterviewSession session = ACTIVE.get(player.getUuid());
        if (session == null) {
            return false;
        }
        String question = session.currentQuestion();
        if (question == null) {
            return false;
        }
        String line = "Q" + (session.index + 1) + ": " + question;
        sendLorekeeperMessage(player, Text.literal(line));
        session.touch();
        return true;
    }

    private static void endInterview(ServerPlayerEntity player) {
        ACTIVE.remove(player.getUuid());
    }

    public static boolean stopInterview(ServerPlayerEntity player) {
        InterviewSession session = ACTIVE.remove(player.getUuid());
        if (session != null) {
            sendLorekeeperMessage(player, Text.literal("Interview ended. The archive is grateful."));
            return true;
        }
        return false;
    }

    public static void clearPending(ServerPlayerEntity player) {
        PENDING.remove(player.getUuid());
    }

    private static List<String> pickQuestions(ServerPlayerEntity player, QuestionPool pool) {
        if (pool == QuestionPool.BASIC) {
            return pickBasicQuestions(player);
        }
        ServerWorld world = (ServerWorld) player.getEntityWorld();
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        int start = data.nextQuestionStart(player.getUuid(), MAIN_QUESTIONS.size(), QUESTIONS_PER_INTERVIEW);
        List<String> selected = new ArrayList<>(QUESTIONS_PER_INTERVIEW);
        for (int i = 0; i < QUESTIONS_PER_INTERVIEW; i++) {
            int index = (start + i) % MAIN_QUESTIONS.size();
            selected.add(MAIN_QUESTIONS.get(index));
        }
        return selected;
    }

    private static List<String> pickBasicQuestions(ServerPlayerEntity player) {
        net.minecraft.util.math.random.Random random = player.getEntityWorld().getRandom();
        List<String> pool = new ArrayList<>(BASIC_QUESTIONS);
        List<String> selected = new ArrayList<>(QUESTIONS_PER_INTERVIEW);
        for (int i = 0; i < QUESTIONS_PER_INTERVIEW && !pool.isEmpty(); i++) {
            int index = random.nextInt(pool.size());
            selected.add(pool.remove(index));
        }
        return selected;
    }

    private static void sendLorekeeperMessage(ServerPlayerEntity player, Text message) {
        player.sendMessage(Text.literal("Lore Keeper: ").append(message), false);
    }

    private static String pickFallbackReaction(ServerPlayerEntity player) {
        net.minecraft.util.math.random.Random random = player.getEntityWorld().getRandom();
        return FALLBACK_REACTIONS.get(random.nextInt(FALLBACK_REACTIONS.size()));
    }

    private static boolean isOptedOut(ServerPlayerEntity player) {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null && !config.interviewAllowOptOut) {
            return false;
        }
        net.minecraft.server.world.ServerWorld world = (net.minecraft.server.world.ServerWorld) player.getEntityWorld();
        LorekeeperPlayerData data = LorekeeperPlayerData.get(world.getServer());
        return data.isInterviewOptedOut(player.getUuid());
    }

    private static void onServerTick(MinecraftServer server) {
        long timeoutMillis = getTimeoutMillis();
        if (timeoutMillis <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        ACTIVE.entrySet().removeIf(entry -> {
            UUID playerId = entry.getKey();
            InterviewSession session = entry.getValue();
            if (now - session.lastActivityMillis <= timeoutMillis) {
                return false;
            }
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(playerId);
            if (player != null) {
                sendLorekeeperMessage(player, Text.literal("The interview window has passed. We can speak later."));
            }
            return true;
        });
    }

    private static long getTimeoutMillis() {
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        int seconds = config != null ? config.interviewTimeoutSeconds : 120;
        if (seconds <= 0) {
            return 0L;
        }
        return seconds * 1000L;
    }

    private static final class InterviewSession {
        private final List<String> questions;
        private int index;
        private boolean awaitingReaction;
        private long lastActivityMillis;

        private InterviewSession(List<String> questions) {
            this.questions = questions;
            this.index = 0;
            this.awaitingReaction = false;
        }

        private void touch() {
            lastActivityMillis = System.currentTimeMillis();
        }

        private String currentQuestion() {
            if (index < 0 || index >= questions.size()) {
                return null;
            }
            return questions.get(index);
        }

        private void advance() {
            index++;
        }
    }

    public enum QuestionPool {
        BASIC,
        MAIN
    }
}
