package de.erethon.hephaestus.listeners;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.papyrus.ContainerLoadEvent;
import de.erethon.papyrus.PlayerInventoryLoadEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class InventoryListener implements Listener {

    private final Hephaestus plugin;

    public InventoryListener(Hephaestus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onPlayerInventoryLoad(PlayerInventoryLoadEvent event) {
        for (ItemStack item : event.items) {
            onItemLoad(item);
        }
        for (ItemStack item : event.armor) {
            onItemLoad(item);
        }
        for (ItemStack item : event.offHand) {
            onItemLoad(item);
        }
    }

    @EventHandler
    private void onContainerLoad(ContainerLoadEvent event){
        for (ItemStack item : event.stacks) {
            onItemLoad(item);
        }
    }

    private void onItemLoad(ItemStack item) {
        if (!item.has(DataComponents.CUSTOM_DATA)) {
            // Handle vanilla items on first appearance here
            return;
        }
        CompoundTag tag = item.get(DataComponents.CUSTOM_DATA).copyTag();
        NamespacedKey key = NamespacedKey.fromString(tag.getString("hephaestus-id"));
        plugin.getLibrary().get(key).update(item);
    }
}
