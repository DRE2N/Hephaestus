package de.erethon.hephaestus.items.interactions;

import de.erethon.hephaestus.items.HItemStack;
import org.bukkit.event.entity.EntityDropItemEvent;

@FunctionalInterface
public interface HItemDropAction {

        void onDrop(HItemStack stack, EntityDropItemEvent event);
}
