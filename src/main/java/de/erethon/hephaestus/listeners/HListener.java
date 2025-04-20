package de.erethon.hephaestus.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.events.HItemEquipEvent;
import de.erethon.hephaestus.events.HItemUnequipEvent;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.papyrus.events.ContainerLoadEvent;
import de.erethon.papyrus.events.PlayerInventoryLoadEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.util.Collections;

public class HListener implements Listener {

    public HListener(Hephaestus plugin) {
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
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = event.equipment.get(slot);
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

    @EventHandler
    private void onEquipmentChange(PlayerArmorChangeEvent event) {
        org.bukkit.inventory.ItemStack oldItem = event.getOldItem();
        org.bukkit.inventory.ItemStack newItem = event.getNewItem();
        // Unequipping
        if (newItem.getType() == org.bukkit.Material.AIR) {
            HItemStack stack = Hephaestus.getStack(oldItem);
            if (stack == null) {
                return;
            }
            stack.getItem().runUnequipActions(stack, event);
            new HItemUnequipEvent(stack.getItem(), event.getPlayer(), event).callEvent();
            return;
        }
        // A damaged item is a different item, filter it out
        if (newItem.matchesWithoutData(oldItem, Collections.singleton(DataComponentTypes.DAMAGE))) {
            return;
        }
        // Equipping
        HItemStack stack = Hephaestus.getStack(newItem);
        if (stack == null) {
            return;
        }
        stack.getItem().runEquipActions(stack, event);
        new HItemEquipEvent(stack.getItem(), event.getPlayer(), event).callEvent();
    }

    @EventHandler
    private void onInteract(PlayerInteractEvent event) {
        if (!(event.hasItem() && event.getItem() != null)) {
            return;
        }
        org.bukkit.inventory.ItemStack item = event.getItem();
        if (item.getType() == org.bukkit.Material.AIR) {
            return;
        }
        HItemStack stack = Hephaestus.getStack(item);
        if (stack == null) {
            return;
        }
        stack.getItem().runInteractActions(stack, event);
    }

    private HItemStack onItemLoad(ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

    private HItemStack onItemLoad(org.bukkit.inventory.ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

}
