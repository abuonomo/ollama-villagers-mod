package com.ollamavillagers;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTextDisplayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaVillagers.MOD_ID);
    // Track which messages have had TTS generated to avoid duplicates
    private Set<String> ttsGeneratedMessages = new HashSet<>();

    private class VillagerTextData {
        public Text originalName;
        public long elapsed = 0;
        public String currentMessage = null;
        public LinkedList<String> messageQueue = new LinkedList<>();
        // Track what text has been spoken for a villager
        public String lastSpokenText = null;
        // Track the full message for TTS purposes
        public String fullMessage = null;
    }

    private Map<VillagerEntity, VillagerTextData> vdata;
    private TextToSpeechService ttsService;

    public VillagerTextDisplayer() {
        vdata = new HashMap<VillagerEntity, VillagerTextData>();
        ttsService = TextToSpeechService.getInstance();
    }

    /**
     * Called when a new message token is received from the LLM.
     * This is used for visual display of text.
     */
    public synchronized void display(VillagerEntity villager, ServerWorld world, String text) {
        VillagerTextData data;
        if (!vdata.containsKey(villager)) {
            data = new VillagerTextData();
            data.originalName = villager.getCustomName();
            data.fullMessage = "";
            vdata.put(villager, data);
        } else {
            data = vdata.get(villager);
        }

        if (data.currentMessage != null && data.messageQueue.isEmpty() &&
            (data.currentMessage + text).length() <= ConfigManager.config.maxTextChars) {
            // Append to current message for display
            data.currentMessage += text;
        } else {
            // Queue for later display
            data.messageQueue.addLast(text);
        }

        // Always accumulate for TTS purposes
        if (data.fullMessage == null) {
            data.fullMessage = text;
        } else {
            data.fullMessage += text;
        }
    }

    /**
     * Called when a complete response is received from the LLM.
     * This allows us to start TTS generation early before visual display is complete.
     */
    public synchronized void completeMessage(VillagerEntity villager, ServerWorld world, String completeMessage) {
        if (!ConfigManager.config.tts.enabled) return;

        // Create a unique identifier for this message
        String messageId = villager.getUuid() + "-" + completeMessage.hashCode();

        // Check if TTS has already been generated for this message
        if (ttsGeneratedMessages.contains(messageId)) {
            return;
        }

        // Mark as generated to avoid duplicates
        ttsGeneratedMessages.add(messageId);
        LOGGER.info("Starting early TTS generation for completed message.");

        // Generate TTS in a separate thread
        new Thread(() -> {
            ttsService.playTextForNearbyPlayers(villager, world, completeMessage);
        }).start();

        // Cleanup - keep the set from growing too large
        if (ttsGeneratedMessages.size() > 100) {
            // Remove random entries if the set gets too big
            while (ttsGeneratedMessages.size() > 50) {
                ttsGeneratedMessages.remove(ttsGeneratedMessages.iterator().next());
            }
        }
    }

    public synchronized void tick() {
        LinkedList<VillagerEntity> trash = new LinkedList<>();
        for (Map.Entry<VillagerEntity, VillagerTextData> kvp : vdata.entrySet()) {
            VillagerEntity villager = kvp.getKey();
            VillagerTextData data = kvp.getValue();
            data.elapsed++;
            long maxLife = 0;
            if (data.currentMessage != null)
                maxLife = ConfigManager.config.textHoldTicks
                        + (int) (data.currentMessage.length() / ConfigManager.config.textCharsPerTick)
                        + 1;
            if (data.currentMessage == null || data.elapsed > maxLife) {
                // Handle new messages or ending messages
                if (!data.messageQueue.isEmpty()) {
                    data.elapsed = 0;
                    data.currentMessage = data.messageQueue.removeFirst();
                    while (data.currentMessage.length() <= ConfigManager.config.maxTextChars && !data.messageQueue.isEmpty())
                        data.currentMessage += data.messageQueue.removeFirst();
                    villager.setCustomNameVisible(true);
                    villager.setAiDisabled(true);
                } else if (data.currentMessage != null) {
                    data.currentMessage = null;
                    data.fullMessage = null;
                    data.lastSpokenText = null;
                    villager.setCustomName(kvp.getValue().originalName);
                    villager.setCustomNameVisible(false);
                    villager.setAiDisabled(false);
                    trash.add(villager);
                }
            }

            if (data.currentMessage != null) {
                int ichar = (int) (data.elapsed * ConfigManager.config.textCharsPerTick);
                String m = data.currentMessage.substring(0, Math.min(data.currentMessage.length(), ichar));
                villager.setCustomName(Text.of(m.trim()));
            }
        }

        for (VillagerEntity e : trash) {
            vdata.remove(e);
        }
    }
}