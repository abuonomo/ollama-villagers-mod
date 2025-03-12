package com.ollamavillagers;

import java.util.HashMap;
import java.util.Map;

import javax.swing.text.html.parser.Entity;

import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;

public class VillagerTextDisplayer {
    private Map<VillagerEntity, ArmorStandEntity> stands;
    
    public VillagerTextDisplayer()
    {
        stands = new HashMap<VillagerEntity, ArmorStandEntity>();
    }

    public void display(VillagerEntity villager, ServerWorld world, String text)
    {
        ArmorStandEntity stand;
        if(!stands.containsKey(villager))
        {
            stand = new ArmorStandEntity(EntityType.ARMOR_STAND, world);
            stands.put(villager, stand);
            stand.setInvisible(true);
			stand.setCustomNameVisible(true);
			world.spawnEntity(stand);
        } else { stand = stands.get(villager); }

        stand.setCustomName(Text.of(text));
    }

    public void tick()
    {
        // stand.updatePosition(pos.getX(), pos.getY(), pos.getZ());
        VillagerEntity e;
        e.isAlive()
    }
}
