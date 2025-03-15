package com.ollamavillagers;

import java.util.ArrayList;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

public class ChatInstance {
    private VillagerTextDisplayer displayer;
    private VillagerEntity villager;
    private String prompt;
    private ServerWorld world;
    private ArrayList<OllamaStream.Message> messages = new ArrayList<>();

    public ChatInstance(ServerWorld world, VillagerTextDisplayer displayer, VillagerEntity villager, String prompt) {
        this.displayer = displayer;
        this.prompt = prompt;
        this.villager = villager;
        this.world = world;
        messages.add(new OllamaStream.Message(OllamaStream.Message.Role.SYSTEM, prompt));
    }

    public synchronized void handleMessage(String username, String message) {
        String chatInput = username + " said \"" + message + "\".";
        messages.add(new OllamaStream.Message(OllamaStream.Message.Role.USER, chatInput));
        OllamaStream stream = new OllamaStream().addMessages(messages.toArray(new OllamaStream.Message[messages.size()]));
        stream.beginChat();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    String completedMessage = "";
                    while(!stream.isOver()) {
                        String token = stream.getNext().content;
                        completedMessage += token;
                        displayer.display(villager, world, token);
                    }

                    // Once we have the complete message, trigger TTS generation immediately
                    if (ConfigManager.config.tts.enabled && !completedMessage.trim().isEmpty()) {
                        // Notify the displayer that we have a complete message
                        displayer.completeMessage(villager, world, completedMessage.trim());
                    }

                    messages.add(new OllamaStream.Message(OllamaStream.Message.Role.ASSISTANT, completedMessage));
                } catch (Exception e) {
                    OllamaVillagers.LOGGER.error("Failed to get a chat message from Ollama!", e);
                }
            }
        }).start();
    }
}