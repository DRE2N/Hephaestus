package de.erethon.hephaestus.items.interactions;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hephaestus.items.HItemStack;
import org.bukkit.event.player.PlayerInteractEvent;

@FunctionalInterface
public interface HItemEquipmentChangeAction {

    void onEquip(HItemStack stack, PlayerArmorChangeEvent event);
}
