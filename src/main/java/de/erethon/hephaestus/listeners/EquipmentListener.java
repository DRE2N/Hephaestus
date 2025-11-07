package de.erethon.hephaestus.listeners;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import de.erethon.hecate.Hecate;
import de.erethon.hecate.classes.HClass;
import de.erethon.hecate.data.HCharacter;
import de.erethon.hecate.data.HPlayer;
import de.erethon.hecate.events.CombatModeReason;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.sets.EquipmentSet;
import de.erethon.papyrus.events.ItemModifierAddEvent;
import de.erethon.spellbook.api.TraitData;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseArmorEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MainHand;

import java.util.Collections;
import java.util.Set;

public class EquipmentListener implements Listener {

    private final Hephaestus plugin = Hephaestus.INSTANCE;

    @EventHandler
    private void onEquip(InventoryClickEvent event) {
        Player player = (Player) event.getWhoClicked();
        HCharacter hCharacter = getCharacterFromPlayer(player);
        if (hCharacter == null) {
            return;
        }
        HClass hClass = hCharacter.getHClass();
        if (hClass == null) {
            return;
        }
        if (event.getSlotType() != InventoryType.SlotType.ARMOR) {
            if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
                if (!canUse(event.getCurrentItem(), player, hCharacter) || !canEquipLevel(player, event.getCurrentItem(), hCharacter)) {
                    player.sendRichMessage("<red>You cannot equip this item!");
                    event.setCancelled(true);
                }
                return;
            }
            return;
        }
        if (event.getClick() != ClickType.LEFT) {
            event.setCancelled(true); // Don't care enough to deal with all the edge cases
            player.updateInventory();
            return;
        }
        // Setting an item in the armor slot
        if (event.getCursor().getType() != Material.AIR && (event.getCurrentItem() == null || event.getCurrentItem().getType() == Material.AIR)) {
            if (!canUse(event.getCurrentItem(), player, hCharacter) || !canEquipLevel(player, event.getCurrentItem(), hCharacter)) {
                player.sendRichMessage("<red>You cannot equip this item!");
                event.setCancelled(true);
            }
        }
    }

    // Right-clicking an armor item
    @EventHandler
    private void onInteract(PlayerInteractEvent event) {
        if (!event.hasItem()) {
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        HCharacter hCharacter = getCharacterFromPlayer(player);
        if (hCharacter == null) {
            return;
        }
        HClass hClass = hCharacter.getHClass();
        if (hClass == null) {
            return;
        }
        if (!canUse(event.getItem(), player, hCharacter) || !canEquipLevel(player, event.getItem(), hCharacter)) {
            player.sendRichMessage("<red>You cannot equip this item!");
            event.setCancelled(true);
            event.setUseItemInHand(Event.Result.DENY);
        }
    }

    @EventHandler
    private void onArmorChange(PlayerArmorChangeEvent event) {
        if (event.getNewItem().matchesWithoutData(event.getOldItem(), Set.of(DataComponentTypes.DAMAGE))) {
            return;
        }
        HItemStack stack = Hephaestus.getStack(event.getNewItem());
        if (stack == null) {
            return;
        }
        Player player = event.getPlayer();
        HCharacter hCharacter = getCharacterFromPlayer(player);
        if (hCharacter == null) {
            return;
        }
        HClass hClass = hCharacter.getHClass();
        if (hClass == null) {
            return;
        }
        HItem item = stack.getItem();
        Set<String> itemTags = item.getTags();
        if (itemTags == null || itemTags.isEmpty()) {
            return;
        }
        itemTags.removeIf(tag -> !tag.startsWith("equipmentset."));
        // we do not support multiple tags for equipment sets
        if (itemTags.size() != 1) {
            return;
        }
        String tag = itemTags.iterator().next();
        int taggedItems = 0;
        for (ItemStack armorItem : player.getEquipment().getArmorContents()) {
            if (armorItem == null || armorItem.getType() == Material.AIR) {
                continue;
            }
            HItemStack armorStack = Hephaestus.getStack(armorItem);
            if (armorStack == null) {
                continue;
            }
            HItem armor = armorStack.getItem();
            Set<String> armorTags = armor.getTags();
            if (armorTags != null && armorTags.contains(tag)) {
                taggedItems++;
            }
        }
        EquipmentSet equipmentSet = plugin.getEquipmentManager().getEquipmentSet(tag);
        if (equipmentSet == null) {
            return;
        }
        Set<TraitData> effects = equipmentSet.effects().get(taggedItems);
        if (effects == null || effects.isEmpty()) {
            return;
        }
        for (TraitData effect : effects) {
            if (player.hasTrait(effect)) {
                continue;
            }
            player.addTrait(effect);
        }
    }

    @EventHandler
    private void onDispenseArmor(BlockDispenseArmorEvent event) {
        HItemStack stack = Hephaestus.getStack(event.getItem());
        if (stack == null || !(event.getTargetEntity() instanceof Player player)) {
            return;
        }
        HCharacter hCharacter = getCharacterFromPlayer(player);
        if (hCharacter == null) {
            return;
        }
        HClass hClass = hCharacter.getHClass();
        if (hClass == null) {
            return;
        }
        if (!canUse(event.getItem(), player, hCharacter) || !canEquipLevel(player, event.getItem(), hCharacter)) {
            player.sendRichMessage("<red>You cannot equip this item!");
            event.setCancelled(true);
        }
    }

    @EventHandler
    private void onAttributeApply(ItemModifierAddEvent event)  {
        if (!(event.getLivingEntity() instanceof Player player)) {
            return;
        }
        HCharacter hCharacter = getCharacterFromPlayer(player);
        if (hCharacter == null) {
            return;
        }
        ItemStack item = event.getItemStack();
        if (!canUse(item, player, hCharacter) || !canEquipLevel(player, item, hCharacter)) {
            event.setCancelled(true);
        }
    }

    private boolean canEquipLevel(Player player, ItemStack item, HCharacter character) {
        int characterLevel = character.getLevel();
        HItemStack stack = Hephaestus.getStack(item);
        if (stack == null) {
            return true; // Not a hitem, so we don't care
        }
        int itemLevel = stack.getItemLevel();
        if (itemLevel >= characterLevel) {
            player.sendRichMessage("<red>You need to be character level <gold>" + itemLevel + "</gold> to equip this item!");
            return false;
        }
        return true;
    }

    private boolean canUse(ItemStack stack, Player player, HCharacter hCharacter) {
        HItemStack hItemStack = Hephaestus.getStack(stack);
        if (hItemStack == null) {
            return true; // Not a hitem, so we don't care
        }
        HItem item = hItemStack.getItem();
        Set<String> itemTags = item.getTags();
        Set<String> armorTags = hCharacter.getTraitline().getArmorTags();
        boolean canUse = itemTags == null || armorTags == null || itemTags.isEmpty() || armorTags.isEmpty();
        if (itemTags == null || armorTags == null) {
            player.sendRichMessage("<red>You cannot use this item!");
            return canUse;
        }
        if (itemTags.contains("equipment.armor")) {
            for (String tag : itemTags) {
                if (armorTags.contains(tag)) {
                    canUse = true;
                    break;
                }
            }
        }
        if (!canUse && itemTags.contains("equipment.weapon")) {
            Set<String> tags = hCharacter.getTraitline().getWeaponTags();
            for (String tag : itemTags) {
                if (tags.contains(tag)) {
                    canUse = true;
                    break;
                }
            }
        }
        if (!canUse && itemTags.contains("equipment.accessory")) {
            Set<String> tags = hCharacter.getTraitline().getAccessoryTags();
            for (String tag : itemTags) {
                if (tags.contains(tag)) {
                    canUse = true;
                    break;
                }
            }
        }
        if (!canUse) {
            player.sendRichMessage("<red>Your archetype or discipline cannot use this item!");
        }
        return canEquipLevel(player, stack, hCharacter) && canUse;
    }

    private HCharacter getCharacterFromPlayer(Player player) {
        Hecate hecate = Hecate.getInstance();
        HPlayer hPlayer = hecate.getDatabaseManager().getHPlayer(player);
        return hPlayer.getSelectedCharacter();
    }

    @EventHandler
    public void onModeSwitch(PlayerSwapHandItemsEvent event) {
        Hecate hecate = Hecate.getInstance();
        // The OffHandItem is the item that WOULD BE in the offhand if the event is not cancelled. Thanks Spigot for great method naming!
        HCharacter hCharacter = hecate.getDatabaseManager().getCurrentCharacter(event.getPlayer());
        if (hCharacter == null) {
            return;
        }
        // Admin mode, with any stick
        if (event.getOffHandItem().getType() == Material.STICK && event.getPlayer().hasPermission("hecate.castmode.adminbypass")) {
            hCharacter.switchCastMode(CombatModeReason.HOTKEY, !hCharacter.isInCastMode()); // The ! is important here lol
            event.setCancelled(true);
            return;
        }
        HItemStack offHandItem = HItemStack.getFromStack(event.getOffHandItem());
        if (offHandItem == null || offHandItem.getItem() == null) {
            return;
        }
        if (!canUse(event.getOffHandItem(), event.getPlayer(), hCharacter)) {
            event.setCancelled(true);
            return;
        }
        if (!Collections.disjoint(offHandItem.getItem().getTags(), hCharacter.getTraitline().getWeaponTags())) {
            hCharacter.switchCastMode(CombatModeReason.HOTKEY, !hCharacter.isInCastMode());
            event.setCancelled(true);
            return;
        }
    }


}
