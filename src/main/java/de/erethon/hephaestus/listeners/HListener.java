package de.erethon.hephaestus.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.events.HItemEquipEvent;
import de.erethon.hephaestus.events.HItemUnequipEvent;
import de.erethon.hephaestus.items.Grindstone;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HUpgradeResult;
import de.erethon.papyrus.events.ContainerLoadEvent;
import de.erethon.papyrus.events.PlayerInventoryLoadEvent;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
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
            onItemLoad(item).updateVisuals();
        }
        if (event.equipment == null) { // A player might have no equipment
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack item = event.equipment.get(slot);
            if (item.isEmpty()) {
                continue;
            }
            onItemLoad(item).updateVisuals();
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
        onItemLoad(event.getItem().getItemStack()).updateVisuals();
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
        if (event.getItem() == null) {
            return;
        }
        HItemStack stack = Hephaestus.getStack(event.getItem());
        if (stack == null) {
            return;
        }
        stack.getItem().runInteractActions(stack, event);
        if (event.getAction().isRightClick()) {
            stack.getItem().runRightClickSpells(event.getPlayer(), stack);
        }

        // Handle grindstone interaction
        if (event.getAction().isRightClick() && event.getClickedBlock() != null) {
            Grindstone grindstone = Grindstone.fromBlock(event.getClickedBlock());
            if (grindstone != null) {
                event.setCancelled(true);
                grindstone.onRightClick(event.getPlayer(), event.getItem());
            } else if (event.getClickedBlock().getType() == Material.SMOKER) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    private void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        org.bukkit.inventory.ItemStack cursor = event.getCursor();
        org.bukkit.inventory.ItemStack current = event.getCurrentItem();
        if (cursor == null || current == null) return;
        if (cursor.getType() == org.bukkit.Material.AIR) return;
        if (current.getType() == org.bukkit.Material.AIR) return;
        // Only handle normal placement (not shift-click moving stacks etc.)
        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) return;
        HItemStack orbStack = Hephaestus.getStack(cursor);
        HItemStack targetStack = Hephaestus.getStack(current);
        if (orbStack == null || targetStack == null || orbStack.getItem() == null) return;
        if (!orbStack.getItem().isOrbItem()) return;
        if (!targetStack.hasSockets()) return;
        HUpgradeResult result = targetStack.insertOrb(orbStack);
        if (result == HUpgradeResult.SUCCESS) {
            int amt = cursor.getAmount();
            if (amt <= 1) {
                event.setCursor(new org.bukkit.inventory.ItemStack(org.bukkit.Material.AIR));
            } else {
                cursor.setAmount(amt - 1);
                event.setCursor(cursor);
            }
            event.setCurrentItem(targetStack.getBukkitStack());
            player.sendMessage(Component.text("Socketed orb!", NamedTextColor.GREEN));
            event.setCancelled(true);
            return;
        }
        player.sendMessage(Component.translatable(result.translationKey(), NamedTextColor.RED));
        event.setCancelled(true);
    }

    private HItemStack onItemLoad(ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

    private HItemStack onItemLoad(org.bukkit.inventory.ItemStack item) {
        return HItemStack.getFromStack(item).update();
    }

}
