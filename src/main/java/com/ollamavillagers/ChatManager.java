package com.ollamavillagers;

import java.util.HashMap;
import java.util.UUID;
import java.util.Random;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;

public class ChatManager {
    private HashMap<VillagerEntity, ChatInstance> chats = new HashMap<>();
    private VillagerTextDisplayer displayer;    

    public ChatManager(VillagerTextDisplayer displayer) {
        this.displayer = displayer;
    }

    public void handleMessage(VillagerEntity villager, ServerWorld world, String username, String message){
        ChatInstance instance;
        if(!chats.containsKey(villager))
        {
            String prompt = selectPrompt(villager.getUuid());
            instance = new ChatInstance(world, displayer, villager, prompt);
            chats.put(villager, instance);
        } else { instance = chats.get(villager); }

        instance.handleMessage(username, message);
    }

    private String selectPrompt(UUID uuid) {
        Random rng = new Random(uuid.getMostSignificantBits() ^ uuid.getLeastSignificantBits());
        double weight = rng.nextDouble() * ConfigManager.totalWeights;
        for(ConfigManager.Personality p : ConfigManager.config.personalities) {
            weight -= p.weight;
            if(weight < 0.0) return p.prompt;
        }

        OllamaVillagers.LOGGER.info("ChatManager#selectPrompt has returned out of the loop. This isn't normal.");
        return ConfigManager.config.personalities[ConfigManager.config.personalities.length - 1].prompt;
    }
}
