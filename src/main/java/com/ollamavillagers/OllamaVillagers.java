package com.ollamavillagers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;

public class OllamaVillagers implements ModInitializer {
	public static final String MOD_ID = "ollama-villagers";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
    public void onInitialize() {
        // listen for player chat events
		ServerMessageEvents.CHAT_MESSAGE.register(this::onPlayerChat);
    }

    private void onPlayerChat(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {

		String chatMessage = message.signedBody().content();
		String username = player.getName().toString();
		ServerWorld world = player.getServerWorld();
		VillagerEntity villager = getClosestVillager(player, world);

		if(villager != null)
		{
			BlockPos pos = villager.getBlockPos().add(0, 2, 0); // 2 blocks above the villager
			
			ArmorStandEntity armorStand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
			armorStand.setInvisible(true);
			armorStand.setCustomName(Text.of(chatMessage));
			armorStand.setCustomNameVisible(true);
			
			armorStand.updatePosition(pos.getX(), pos.getY(), pos.getZ());
			world.spawnEntity(armorStand);
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