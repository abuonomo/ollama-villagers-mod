package com.ollamavillagers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import org.slf4j.LoggerFactory;

public class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = new File("ollama-villagers/config.json");

    public static class Personality {
        public String prompt = "You are a Minecraft villager, talking to the main player.";
        public double weight = 1.0;
    }

    public static class TTSConfig {
        public boolean enabled = false;
        public String apiKey = "";
        public String voice = "alloy"; // OpenAI voice options: alloy, echo, fable, onyx, nova, shimmer
        public float volume = 1.0f;
        public int maxCacheItems = 100; // How many audio clips to cache
        public boolean notifierEnabled = true; // Enable UDP notifications
        public int notifierPort = 25566; // Port for UDP notifications
        public String cacheDir = "ollama-villagers/tts-cache"; // Directory for cached audio files
    }

    public static class Config {
        public Personality[] personalities = { new Personality() };
        public String host = "http://localhost:11434/";
        public String model = "mistral";
        public String keepAlive = "30m";
        public int maxTextChars = 40;
        public int textHoldTicks = 40;
        public double textCharsPerTick = 1.0;
        public long requestTimeoutSeconds = 60;
        public TTSConfig tts = new TTSConfig();
    }

    public static double totalWeights = 1.0;

    public static Config config = new Config();

    public static void loadConfig() {
        if (!CONFIG_FILE.exists()) {
            saveConfig();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            config = GSON.fromJson(reader, Config.class);

            totalWeights = 0.0;
            for(Personality p : config.personalities) {
                totalWeights += p.weight;
            }

        } catch (IOException | JsonSyntaxException e) {
            e.printStackTrace();
        }
    }

    public static void saveConfig() {
        CONFIG_FILE.getParentFile().mkdirs();
        LoggerFactory.getLogger("ollama-villagers").info("Trying to save.");
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            GSON.toJson(config, writer);
            LoggerFactory.getLogger("ollama-villagers").info("Trying to save.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}