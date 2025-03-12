package com.ollamavillagers;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class VillagerTextDisplayer {
    private class VillagerTextData
    {
        public Text originalName;
        public int lifetime = -1;
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

        LinkedList<String> splitString = new LinkedList<>();
        String[] words = text.split(" ");
        int nChars = 0;
        String sequence = "";
        for(int i = 0; i < words.length; ++i)
        {
            if(nChars == 0)
            {
                nChars += words[i].length();
                sequence += words[i];
                continue;
            }

            if(nChars + words[i].length() + 1 > ConfigManager.config.maxTextChars)
            {
                splitString.add(sequence);
                sequence = words[i];
                nChars = words[i].length();
                continue;
            }

            sequence += " " + words[i];
            nChars += words[i].length() + 1;
        }
        if(!sequence.equals("")) splitString.add(sequence);

        for(String s : splitString)
            data.messageQueue.addLast(s);
    }

    public synchronized void tick()
    {
        LinkedList<VillagerEntity> trash = new LinkedList<>();
        for (Map.Entry<VillagerEntity, VillagerTextData> kvp : vdata.entrySet()) {
            VillagerEntity villager = kvp.getKey();
            VillagerTextData data = kvp.getValue();
            data.lifetime--;
            if(data.lifetime < 0)
            {
                if(data.messageQueue.isEmpty())
                {
                    villager.setCustomName(kvp.getValue().originalName);
                    villager.setCustomNameVisible(false);
                    trash.add(villager);
                }
                else
                {
                    data.lifetime = ConfigManager.config.textLifetimeTicks;
                    villager.setCustomName(Text.of(data.messageQueue.removeFirst()));
                    villager.setCustomNameVisible(true);
                }
            }
        }        

        for(VillagerEntity e : trash)
        {
            vdata.remove(e);
        }
    }
}
