package com.ollamavillagers;

import io.github.ollama4j.OllamaAPI;
import io.github.ollama4j.models.chat.OllamaChatMessageRole;
import io.github.ollama4j.models.chat.OllamaChatRequest;
import io.github.ollama4j.models.chat.OllamaChatRequestBuilder;
import io.github.ollama4j.models.chat.OllamaChatResult;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

public class ChatInstance {
    private VillagerTextDisplayer displayer;
    private VillagerEntity villager;
    private OllamaAPI ollamaAPI;
    private String prompt;
    private ServerWorld world;
    private OllamaChatRequestBuilder builder;
    private OllamaChatRequest requestModel;
    private OllamaChatResult chatResult = null;

    public ChatInstance(ServerWorld world, VillagerTextDisplayer displayer, VillagerEntity villager, String prompt) {
        this.displayer = displayer;

        ollamaAPI = new OllamaAPI(ConfigManager.config.host);
        ollamaAPI.setRequestTimeoutSeconds(ConfigManager.config.requestTimeoutSeconds);
        builder = OllamaChatRequestBuilder.getInstance(ConfigManager.config.model).withStreaming();
        this.prompt = prompt;
        this.villager = villager;
    }

    public synchronized void handleMessage(String username, String message) {
        String chatInput = username + " said \"" + message + "\".";
        if(chatResult == null) {
            requestModel = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, prompt)
                .withMessage(OllamaChatMessageRole.USER, chatInput)
                .withStreaming()
                .build();
        } else {
            requestModel = builder.withMessages(chatResult.getChatHistory()).withMessage(OllamaChatMessageRole.USER, chatInput).withStreaming().build();
        }

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    OllamaVillagers.LOGGER.info("Chatting... ollamaAPI is " + ollamaAPI.toString());
                    OllamaChatResult result = ollamaAPI.chat(requestModel);
                    OllamaVillagers.LOGGER.info("Chat sent.");
                    displayer.display(villager, world, result.getChatHistory().get(result.getChatHistory().size() - 1).getContent());
                    setResult(result);
                } catch (Exception e) {
                    OllamaVillagers.LOGGER.error("Failed to get a chat message from Ollama!", e);
                }
            }
        }).start();
    }

    private synchronized void setResult(OllamaChatResult result) {
        chatResult = result;
    }
}
