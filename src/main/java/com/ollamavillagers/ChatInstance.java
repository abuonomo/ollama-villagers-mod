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
        builder = OllamaChatRequestBuilder.getInstance(ConfigManager.config.model);
        this.prompt = prompt;
        this.villager = villager;
    }

    public synchronized void handleMessage(String username, String message) {
        String chatInput = username + " said \"" + message + "\".";
        if(chatResult == null) {
            requestModel = builder
                .withMessage(OllamaChatMessageRole.SYSTEM, prompt)
                .withMessage(OllamaChatMessageRole.USER, chatInput)
                .build();
        } else {
            requestModel = builder.withMessages(chatResult.getChatHistory()).withMessage(OllamaChatMessageRole.USER, chatInput).build();
        }

        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    OllamaChatResult result = ollamaAPI.chat(requestModel);
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
