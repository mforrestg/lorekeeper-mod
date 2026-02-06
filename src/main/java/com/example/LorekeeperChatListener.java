package com.example;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

public final class LorekeeperChatListener {
    private LorekeeperChatListener() {}

    public static void register() {
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register(LorekeeperChatListener::onAllowChatMessage);
    }

    private static boolean onAllowChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        if (sender == null) {
            return true;
        }
        String content = message.getContent().getString();
        boolean handled = LorekeeperInterviewManager.handleChatAnswer(sender, content);
        if (!handled) {
            return true;
        }
        LorekeeperConfig config = LorekeeperMod.CONFIG;
        if (config != null && config.interviewPrivateResponses) {
            return false;
        }
        return true;
    }
}
