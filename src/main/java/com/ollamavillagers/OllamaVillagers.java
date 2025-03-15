package com.ollamavillagers;

import net.fabricmc.api.ModInitializer;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

public class OllamaVillagers implements ModInitializer {
    public static final String MOD_ID = "ollama-villagers";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private VillagerTextDisplayer displayer = new VillagerTextDisplayer();
    private ChatManager chatManager = new ChatManager(displayer);
    private TextToSpeechService ttsService;

    @Override
    public void onInitialize() {
        // Load configuration
        ConfigManager.loadConfig();

        // Initialize services
        ttsService = TextToSpeechService.getInstance();

        // Log TTS status
        if (ConfigManager.config.tts.enabled) {
            LOGGER.info("Text-to-Speech feature is enabled");
            if (ConfigManager.config.tts.apiKey.isEmpty()) {
                LOGGER.warn("OpenAI API key is not set. TTS will not function.");
            }
        } else {
            LOGGER.info("Text-to-Speech feature is disabled");
        }

        // ✅ NEW: Register player in single-player mode
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStart);

        // ✅ Multiplayer: Register player connections dynamically
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            String username = handler.getPlayer().getName().getString();
            String ipAddress = handler.getConnectionAddress().toString();

            // Strip the port part from the address
            if (ipAddress.contains(":")) {
                ipAddress = ipAddress.substring(0, ipAddress.indexOf(":"));
            }
            ipAddress = ipAddress.replace("/", "");

            LOGGER.info("Player {} connected from {}", username, ipAddress);
            ttsService.registerPlayer(username, ipAddress);
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            String username = handler.getPlayer().getName().getString();
            ttsService.unregisterPlayer(username);
        });

        // Register event handlers
        ServerMessageEvents.CHAT_MESSAGE.register(this::onPlayerChat);
        ServerTickEvents.END_SERVER_TICK.register(listener -> {
            displayer.tick();
        });

        // Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutting down TTS service...");
            ttsService.shutdown();
        }));
    }

    /**
     * ✅ New method: Handles single-player mode
     */
private void onServerStart(MinecraftServer server) {
    if (server.isDedicated()) {
        LOGGER.info("Running in multiplayer mode. Player registration will happen dynamically.");
    } else {
        LOGGER.info("Running in single-player mode. Registering local player...");

        // Get the first (and only) player in single-player mode
        String username = server.getPlayerManager().getPlayerList().isEmpty()
                ? "UnknownPlayer"
                : server.getPlayerManager().getPlayerList().get(0).getName().getString();

        String ipAddress = "127.0.0.1";

        LOGGER.info("Registering local single-player: {} at {}", username, ipAddress);
        ttsService.registerPlayer(username, ipAddress);
    }
}


    private void onPlayerChat(SignedMessage message, ServerPlayerEntity player, MessageType.Parameters parameters) {
        String chatMessage = message.signedBody().content();
        String username = player.getName().getLiteralString();
        ServerWorld world = player.getServerWorld();
        VillagerEntity villager = getClosestVillager(player, world);
        if (villager != null) {
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

        if (closestDistance > 100.0) closestVillager = null;

        return closestVillager;
    }
}
