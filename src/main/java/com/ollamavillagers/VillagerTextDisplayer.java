package com.ollamavillagers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

public class VillagerTextDisplayer {
    private class VillagerTextData
    {
        public Text originalName;
        public long elapsed = 0;
        public String currentMessage = null;
        public LinkedList<String> messageQueue = new LinkedList<>();
    }

    private Map<VillagerEntity, VillagerTextData> vdata;

    public VillagerTextDisplayer()
    {
        vdata = new HashMap<VillagerEntity, VillagerTextData>();
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
        } else {
            data.messageQueue.addLast(text);
        }
    }

    public synchronized void tick()
    {
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
            if(data.currentMessage == null || data.elapsed > maxLife)
            {
                if(!data.messageQueue.isEmpty())
                {
                    data.elapsed = 0;
                    data.currentMessage = data.messageQueue.removeFirst();
                    while(data.currentMessage.length() <= ConfigManager.config.maxTextChars && !data.messageQueue.isEmpty())
                        data.currentMessage += data.messageQueue.removeFirst();
                    villager.setCustomNameVisible(true);
                    villager.setAiDisabled(true);
                }
                else if(data.currentMessage != null)
                {
                    data.currentMessage = null;
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
            }
        }        

        for(VillagerEntity e : trash)
        {
            vdata.remove(e);
        }
    }
}
