package com.ollamavillagers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public class OllamaVillagers implements ModInitializer {
	public static final String MOD_ID = "ollama-villagers";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	private VillagerTextDisplayer displayer = new VillagerTextDisplayer();
	private ChatManager chatManager = new ChatManager(displayer);

	@Override
    public void onInitialize() {

		ConfigManager.loadConfig();

		ServerMessageEvents.CHAT_MESSAGE.register(this::onPlayerChat);
		ServerTickEvents.END_SERVER_TICK.register(listener -> {
			displayer.tick();
        });
    }

    private void onPlayerChat(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
		String chatMessage = message.signedBody().content();
		String username = player.getName().getLiteralString();
		ServerWorld world = player.getServerWorld();
		VillagerEntity villager = getClosestVillager(player, world);
		if(villager != null)
		{
			chatManager.handleMessage(villager, world, username, chatMessage);
		}
    }

    private VillagerEntity getClosestVillager(PlayerEntity player, ServerWorld world) {
        double closestDistance = Double.MAX_VALUE;
        VillagerEntity closestVillager = null;
        
        for (VillagerEntity villager : world.getEntitiesByClass(VillagerEntity.class, player.getBoundingBox().expand(10), entity -> true)) {
            double distance = player.squaredDistanceTo(villager);
            if (distance < closestDistance) {
                closestDistance = distance;
                closestVillager = villager;
            }
        }

		if(closestDistance > 100.0) closestVillager = null;
        
        return closestVillager;
    }
}