package com.ollamavillagers;

import com.google.gson.JsonObject;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

public class TextToSpeechService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaVillagers.MOD_ID);
    private static String TTS_API_ENDPOINT = ConfigManager.config.tts.endpoint;

    private static String TTS_CACHE_DIR = "ollama-villagers/tts-cache";
    private static final OkHttpClient HTTP_CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();

    private final Map<String, Path> audioCache = new LinkedHashMap<String, Path>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Path> eldest) {
            return size() > ConfigManager.config.tts.maxCacheItems;
        }
    };

    private static TextToSpeechService instance;
    private AudioNotifier audioNotifier;

    private TextToSpeechService() {
        // Create the cache directory if it doesn't exist
        try {
            if (ConfigManager.config.tts.cacheDir != null) {
                TTS_CACHE_DIR = ConfigManager.config.tts.cacheDir;
            }
            Files.createDirectories(Paths.get(TTS_CACHE_DIR));
        } catch (IOException e) {
            LOGGER.error("Failed to create TTS cache directory", e);
        }

        // Initialize the audio notifier
        try {
            this.audioNotifier = new AudioNotifier();
        } catch (Exception e) {
            LOGGER.error("Failed to initialize audio notifier", e);
            this.audioNotifier = null;
        }
    }

    public static TextToSpeechService getInstance() {
        if (instance == null) {
            instance = new TextToSpeechService();
        }
        return instance;
    }

    /**
     * Register a player's IP address when they join
     */
    public void registerPlayer(String username, String ipAddress) {
        if (audioNotifier != null) {
            audioNotifier.registerPlayer(username, ipAddress);
        }
    }

    /**
     * Unregister a player when they leave
     */
    public void unregisterPlayer(String username) {
        if (audioNotifier != null) {
            audioNotifier.unregisterPlayer(username);
        }
    }

    /**
     * Shutdown the service and clean up resources
     */
    public void shutdown() {
        if (audioNotifier != null) {
            audioNotifier.shutdown();
        }
        LOGGER.info("TTS service shutdown complete");
    }

    public void playTextForNearbyPlayers(VillagerEntity villager, ServerWorld world, String text) {
        if (!ConfigManager.config.tts.enabled || ConfigManager.config.tts.apiKey.isEmpty()) {
            return;
        }

        try {
            // Generate a deterministic ID based on text and voice to ensure caching works properly
            String textHash = text.trim().toLowerCase() + ConfigManager.config.tts.voice;
            String cacheId = String.valueOf(textHash.hashCode());

            // Check if we already have this audio cached
            Path audioPath = audioCache.get(cacheId);
            if (audioPath == null) {
                // Not in cache, need to generate it
                audioPath = generateSpeech(text, cacheId);
                if (audioPath != null) {
                    audioCache.put(cacheId, audioPath);
                } else {
                    return; // Failed to generate speech
                }
            }

            // Find nearby players and notify about the audio
            for (ServerPlayerEntity player : world.getPlayers()) {
                if (player.squaredDistanceTo(villager) <= 100.0) { // 10 block radius (squared)
                    // Get player name for notification
                    String playerName = player.getName().getString();

                    // Get villager identifier (use UUID or custom name if available)
                    String villagerName = villager.getCustomName() != null
                        ? villager.getCustomName().getString()
                        : "Villager-" + villager.getUuid().toString().substring(0, 8);

                    // Notify about the available audio
                    if (audioNotifier != null) {
                        audioNotifier.notifyAudioAvailable(
                            audioPath.toString(),
                            playerName,
                            villagerName
                        );
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error playing TTS audio", e);
        }
    }

    private Path generateSpeech(String text, String cacheId) {
        if (text.trim().isEmpty()) {
            LOGGER.warn("TTS request aborted: Empty text provided.");
            return null;
        }

        Path outputPath = Paths.get(TTS_CACHE_DIR, cacheId + ".mp3");

        // If cached file already exists, return it
        if (Files.exists(outputPath)) {
            LOGGER.info("Using cached TTS file: {}", outputPath);
            return outputPath;
        }

        LOGGER.info("Generating new TTS audio: text=\"{}\", cacheId={}", text, cacheId);

        try {
            // Create JSON request body
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", ConfigManager.config.tts.model);
            requestBody.addProperty("input", text);
            requestBody.addProperty("voice", ConfigManager.config.tts.voice);

            LOGGER.info("Sending TTS request to OpenAI: {}", requestBody);

            // Build the HTTP request
            Request request = new Request.Builder()
                    .url(TTS_API_ENDPOINT)
                    .post(RequestBody.create(
                            requestBody.toString(),
                            MediaType.parse("application/json")))
                    .header("Authorization", "Bearer " + ConfigManager.config.tts.apiKey)
                    .build();

            // Execute the request
            try (Response response = HTTP_CLIENT.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    LOGGER.error("OpenAI API error: {} {}", response.code(), response.message());
                    return null;
                }

                LOGGER.info("Received TTS response: code={}, content-length={}",
                            response.code(), response.body() != null ? response.body().contentLength() : "Unknown");

                // Save the audio file
                try (InputStream inputStream = response.body().byteStream();
                     BufferedInputStream bufferedInputStream = new BufferedInputStream(inputStream);
                     OutputStream outputStream = Files.newOutputStream(outputPath)) {

                    byte[] buffer = new byte[8192]; // Use a larger buffer
                    int bytesRead;
                    int totalBytes = 0;
                    while ((bytesRead = bufferedInputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                        totalBytes += bytesRead;
                    }
                    outputStream.flush(); // Ensure all data is written

                    LOGGER.info("TTS file saved: {} ({} bytes)", outputPath, totalBytes);
                }

                return outputPath;
            }
        } catch (Exception e) {
            LOGGER.error("Failed to generate speech", e);
            return null;
        }
    }


    public void generateAndPlayTTS(VillagerEntity villager, ServerWorld world, String text) {
    try {
        // Generate a deterministic ID based on text and voice
        String textHash = text.trim().toLowerCase() + ConfigManager.config.tts.voice;
        String cacheId = String.valueOf(textHash.hashCode());

        // Check if already cached
        Path audioPath = audioCache.get(cacheId);
        if (audioPath == null) {
            // Generate new TTS
            audioPath = generateSpeech(text, cacheId);
            if (audioPath != null) {
                audioCache.put(cacheId, audioPath);
            } else {
                return;
            }
        }

        // Notify nearby players
        for (ServerPlayerEntity player : world.getPlayers()) {
            if (player.squaredDistanceTo(villager) <= 100.0) {
                String playerName = player.getName().getString();
                String villagerName = villager.getCustomName() != null
                    ? villager.getCustomName().getString()
                    : "Villager-" + villager.getUuid().toString().substring(0, 8);

                if (audioNotifier != null) {
                    audioNotifier.notifyAudioAvailable(
                        audioPath.toString(),
                        playerName,
                        villagerName
                    );
                }
            }
        }
    } catch (Exception e) {
        LOGGER.error("Error generating/playing TTS audio", e);
    }
}

}