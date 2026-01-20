package com.example;

import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;

public final class LorekeeperChatListener {
    private LorekeeperChatListener() {}

    public static void register() {
        ServerMessageEvents.CHAT_MESSAGE.register(LorekeeperChatListener::onChatMessage);
    }

    private static void onChatMessage(SignedMessage message, ServerPlayerEntity sender, MessageType.Parameters params) {
        if (sender == null) {
            return;
        }
        String content = message.getContent().getString();
        LorekeeperInterviewManager.handleChatAnswer(sender, content);
    }
}
