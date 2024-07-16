package de.erethon.hephaestus.items.interactions;

import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import org.bukkit.event.player.PlayerInteractEvent;

@FunctionalInterface
public interface HItemInteractAction {

    void onInteract(HItemStack stack, PlayerInteractEvent event);
}
