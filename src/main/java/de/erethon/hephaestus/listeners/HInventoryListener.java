package de.erethon.hephaestus.listeners;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.papyrus.ContainerLoadEvent;
import de.erethon.papyrus.PlayerInventoryLoadEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;

public class HInventoryListener implements Listener {

    private final Hephaestus plugin;

    public HInventoryListener(Hephaestus plugin) {
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

    @EventHandler
    private void onPickup(EntityPickupItemEvent event) {
        onItemLoad(ItemStack.fromBukkitCopy(event.getItem().getItemStack()));
    }

    private void onItemLoad(ItemStack item) {
        NamespacedKey key;
        if (!item.has(DataComponents.CUSTOM_DATA)) {
            key = NamespacedKey.fromString(BuiltInRegistries.ITEM.getKey(item.getItem()).toString());
            plugin.getLibrary().runIfPresent(key, i -> i.update(item));
            return;
        }
        CompoundTag tag = item.get(DataComponents.CUSTOM_DATA).copyTag();
        key = NamespacedKey.fromString(tag.getString("hephaestus-id"));
        plugin.getLibrary().runIfPresent(key, i -> i.update(item));
    }

}
