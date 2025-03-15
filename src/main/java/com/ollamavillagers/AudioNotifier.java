package com.ollamavillagers;

import com.google.gson.Gson;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Simple UDP broadcaster to notify client applications when new TTS audio is available
 */
public class AudioNotifier {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaVillagers.MOD_ID);
    private static final int DEFAULT_PORT = 25566; // Default port for broadcasting
    private static final Gson GSON = new Gson();

    private int port;
    private boolean enabled = false;
    private DatagramSocket socket;

    // Store player IP addresses mapped to their usernames
    private Map<String, InetAddress> playerAddresses = new HashMap<>();

    public AudioNotifier() {
        this.port = DEFAULT_PORT;
        try {
            this.enabled = ConfigManager.config.tts.notifierEnabled;
            if (ConfigManager.config.tts.notifierPort > 0) {
                this.port = ConfigManager.config.tts.notifierPort;
            }
            socket = new DatagramSocket();
            LOGGER.info("Audio notifier initialized on port {}", port);
        } catch (Exception e) {
            LOGGER.error("Failed to initialize audio notifier", e);
            this.enabled = false;
        }
    }

    /**
     * Register a player's IP address when they join
     */
    public void registerPlayer(String username, String ipAddress) {
        if (!enabled) return;

        try {
            InetAddress address = InetAddress.getByName(ipAddress);
            playerAddresses.put(username, address);
            LOGGER.info("Registered player {} at {}", username, ipAddress);
        } catch (Exception e) {
            LOGGER.error("Failed to register player address", e);
        }
    }

    /**
     * Unregister a player when they leave
     */
    public void unregisterPlayer(String username) {
        playerAddresses.remove(username);
    }

    /**
     * Notify nearby players that a new audio file is available
     */
    public void notifyAudioAvailable(String audioFilePath, String playerName, String villagerName) {
        if (!enabled) return;

        try {
            // Create notification payload
            Map<String, String> payload = new HashMap<>();
            payload.put("type", "tts_audio");
            payload.put("file", audioFilePath);
            payload.put("villager", villagerName);

            String message = GSON.toJson(payload);
            byte[] buffer = message.getBytes();

            // Send to specific player if known
            if (playerName != null && playerAddresses.containsKey(playerName)) {
                InetAddress playerAddress = playerAddresses.get(playerName);
                DatagramPacket packet = new DatagramPacket(
                    buffer, buffer.length, playerAddress, port
                );
                socket.send(packet);
                LOGGER.info("Sent audio notification to player {}", playerName);
            } else {
                // Broadcast to all known players
                for (Map.Entry<String, InetAddress> entry : playerAddresses.entrySet()) {
                    DatagramPacket packet = new DatagramPacket(
                        buffer, buffer.length, entry.getValue(), port
                    );
                    socket.send(packet);
                }
                LOGGER.info("Broadcast audio notification to all players");
            }
        } catch (Exception e) {
            LOGGER.error("Failed to send audio notification", e);
        }
    }

    /**
     * Clean up resources
     */
    public void shutdown() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }
}