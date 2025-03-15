package com.ollamavillagers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillagerTextDisplayer {
    private static final Logger LOGGER = LoggerFactory.getLogger(OllamaVillagers.MOD_ID);

    private class VillagerTextData
    {
        public Text originalName;
        public long elapsed = 0;
        public String currentMessage = null;
        public LinkedList<String> messageQueue = new LinkedList<>();
        // Track what text has been spoken for a villager
        public String lastSpokenText = null;
    }

    private Map<VillagerEntity, VillagerTextData> vdata;
    private TextToSpeechService ttsService;

    public VillagerTextDisplayer()
    {
        vdata = new HashMap<VillagerEntity, VillagerTextData>();
        ttsService = TextToSpeechService.getInstance();
    }

    public synchronized void display(VillagerEntity villager, ServerWorld world, String text)
    {
        VillagerTextData data;
        if(!vdata.containsKey(villager))
        {
            data = new VillagerTextData();
            data.originalName = villager.getCustomName();
            vdata.put(villager, data);
        } else { data = vdata.get(villager); }

        if(data.currentMessage != null && data.messageQueue.isEmpty() && (data.currentMessage + text).length() <= ConfigManager.config.maxTextChars) {
            data.currentMessage += text;
            // Reset lastSpokenText so that TTS will be regenerated with the full, updated text.
            data.lastSpokenText = null;
        } else {
            data.messageQueue.addLast(text);
        }

    }

    public synchronized void tick() {
        LinkedList<VillagerEntity> trash = new LinkedList<>();
        for (Map.Entry<VillagerEntity, VillagerTextData> kvp : vdata.entrySet()) {
            VillagerEntity villager = kvp.getKey();
            VillagerTextData data = kvp.getValue();
            data.elapsed++;
            long maxLife = 0;
            if(data.currentMessage != null)
                maxLife = ConfigManager.config.textHoldTicks
                            + (int)(data.currentMessage.length() / ConfigManager.config.textCharsPerTick)
                            + 1;
            if(data.currentMessage == null || data.elapsed > maxLife) {
                // Handle new messages or ending messages
                if(!data.messageQueue.isEmpty()) {
                    data.elapsed = 0;
                    data.currentMessage = data.messageQueue.removeFirst();
                    while(data.currentMessage.length() <= ConfigManager.config.maxTextChars && !data.messageQueue.isEmpty())
                        data.currentMessage += data.messageQueue.removeFirst();
                    villager.setCustomNameVisible(true);
                    villager.setAiDisabled(true);
                    // Reset lastSpokenText for the new message
                    data.lastSpokenText = null;
                }
                else if(data.currentMessage != null) {
                    data.currentMessage = null;
                    data.lastSpokenText = null;
                    villager.setCustomName(kvp.getValue().originalName);
                    villager.setCustomNameVisible(false);
                    villager.setAiDisabled(false);
                    trash.add(villager);
                }
            }

            if(data.currentMessage != null) {
                int ichar = (int)(data.elapsed * ConfigManager.config.textCharsPerTick);
                String m = data.currentMessage.substring(0, Math.min(data.currentMessage.length(), ichar));
                villager.setCustomName(Text.of(m.trim()));

                // Only trigger TTS when text is fully displayed and hasn't been spoken yet
                if (ConfigManager.config.tts.enabled &&
                    villager.getWorld() instanceof ServerWorld serverWorld &&
                    data.lastSpokenText == null &&
                    ichar >= data.currentMessage.length()) {  // Message is fully displayed

                    data.lastSpokenText = data.currentMessage;
                    try {
                        // Process TTS in a separate thread to avoid blocking the game
                        new Thread(() -> {
                            ttsService.playTextForNearbyPlayers(villager, serverWorld, data.currentMessage);
                        }).start();
                    } catch (Exception e) {
                        LOGGER.error("Failed to play TTS for villager", e);
                    }
                }
            }
        }

        for(VillagerEntity e : trash) {
            vdata.remove(e);
        }
    }
}