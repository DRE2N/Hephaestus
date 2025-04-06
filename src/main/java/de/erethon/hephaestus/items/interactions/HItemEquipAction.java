package de.erethon.hephaestus.items.interactions;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hephaestus.items.HItemStack;

@FunctionalInterface
public interface HItemEquipAction {

    void onEquip(HItemStack stack, PlayerArmorChangeEvent event);
}
