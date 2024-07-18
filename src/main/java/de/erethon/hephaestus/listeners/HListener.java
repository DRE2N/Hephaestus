package de.erethon.hephaestus.listeners;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.papyrus.ContainerLoadEvent;
import de.erethon.papyrus.PlayerInventoryLoadEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;

import javax.annotation.Nullable;

public class HListener implements Listener {

    private final Hephaestus plugin;

    public HListener(Hephaestus plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    private void onPlayerInventoryLoad(PlayerInventoryLoadEvent event) {
        Player player = (Player) event.player.getBukkitEntity();
        for (ItemStack item : event.items) {
            if (item.isEmpty()) {
                continue;
            }
            onItemLoad(item).updateVisuals(player);
        }
        for (ItemStack item : event.armor) {
            if (item.isEmpty()) {
                continue;
            }
            onItemLoad(item).updateVisuals(player);
        }
        for (ItemStack item : event.offHand) {
            if (item.isEmpty()) {
                continue;
            }
            onItemLoad(item).updateVisuals(player);
        }
    }

    @EventHandler
    private void onContainerLoad(ContainerLoadEvent event){
        for (ItemStack item : event.stacks) {
            if (item.isEmpty()) {
                continue;
            }
            onItemLoad(item);
        }
    }

    @EventHandler
    private void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getItem().getItemStack().getType() == org.bukkit.Material.AIR) {
            return;
        }
        onItemLoad(event.getItem().getItemStack()).updateVisuals(player);
    }

    @EventHandler
    private void onDrop(EntityDropItemEvent event) {
        HItemStack stack = Hephaestus.getStack(event.getItemDrop().getItemStack());
        if (stack == null) {
            return;
        }
        stack.getItem().runDropActions(stack, event);
    }

    private HItemStack onItemLoad(ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

    private HItemStack onItemLoad(org.bukkit.inventory.ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

}
