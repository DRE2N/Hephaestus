package de.erethon.hephaestus.items;

import com.mojang.brigadier.Message;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.hephaestus.utils.HItemTranslationRegistry;
import de.erethon.hephaestus.utils.HUpgradeResult;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// A HItemStack is an item in the world, and has various properties. It references a HItem, which is the base item and acts as a template for the item.
public class HItemStack {

    private HItem item;
    private int itemLevel = 0;
    private HRarity rarity = HRarity.COMMON;
    private int maxUpgrades = 0;
    private final List<HRolledUpgrade> upgrades = new ArrayList<>();

    private ItemStack stack;

    public HItemStack(HItem item) {
        this.item = item;
        stack = new ItemStack(item.getBaseItem());
        stack.applyComponents(item.getPatch());
        update();
    }

    public HItemStack(HItem item, ItemStack stack) {
        this.stack = stack;
        this.item = item;
        loadDataFromNBT();
        stack.applyComponents(item.getPatch());
        update();
    }

    public HItemStack(HItem item, org.bukkit.inventory.ItemStack stack) {
        this.item = item;
        this.stack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        loadDataFromNBT();
        this.stack.applyComponents(item.getPatch());
        update();
    }

    public ItemStack getVanillaStack() {
        return stack;
    }

    public org.bukkit.inventory.ItemStack getBukkitStack() {
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asBukkitCopy(stack);
    }

    public HItem getItem() {
        return item;
    }

    public void setItemLevel(int itemLevel) {
        this.itemLevel = itemLevel;
        saveChanges();
    }

    public int getItemLevel() {
        return itemLevel;
    }

    public void setRarity(HRarity rarity) {
        this.rarity = rarity;
        saveChanges();
    }

    public HRarity getRarity() {
        return rarity;
    }

    public void setMaxUpgrades(int maxUpgrades) {
        this.maxUpgrades = maxUpgrades;
        saveChanges();
    }

    public int getMaxUpgrades() {
        return maxUpgrades;
    }

    public HUpgradeResult rollAndAddUpgrade(String id) {
        if (upgrades.size() >= maxUpgrades) {
            return HUpgradeResult.TOO_MANY_UPGRADES;
        }
        HItemUpgrade upgrade = item.getPlugin().getLibrary().getUpgrade(id);
        if (upgrade == null) {
            return HUpgradeResult.INVALID_UPGRADE;
        }
        if (!item.getAllowedUpgrades().contains(id)) {
            return HUpgradeResult.INVALID_ITEM;
        }
        if (upgrade.getValidItems().stream().noneMatch(i -> i.equals(item.getKey().toString()))) {
            return HUpgradeResult.INVALID_ITEM;
        }
        if (upgrade.getMinimumLevel() > itemLevel) {
            return HUpgradeResult.TOO_LOW_LEVEL;
        }
        boolean hasRequired = true;
        for (String required : upgrade.getRequiredUpgrades()) {
            if (upgrades.stream().noneMatch(u -> u.getUpgrade().getId().equals(required))) {
                hasRequired = false;
                break;
            }
        }
        if (!hasRequired) {
            return HUpgradeResult.MISSING_REQUIRED_UPGRADE;
        }
        boolean hasIncompatible = false;
        for (String incompatible : upgrade.getIncompatibleUpgrades()) {
            if (upgrades.stream().anyMatch(u -> u.getUpgrade().getId().equals(incompatible))) {
                hasIncompatible = true;
                break;
            }
        }
        if (hasIncompatible) {
            return HUpgradeResult.INCOMPATIBLE_UPGRADE;
        }
        HRolledUpgrade rolledUpgrade = upgrade.roll(this);
        if (rolledUpgrade == null) {
            return HUpgradeResult.INVALID_UPGRADE;
        }
        upgrades.add(rolledUpgrade);
        saveChanges();
        item.getPlugin().getLogger().info("Added upgrade " + rolledUpgrade.getId() + " to item " + item.getKey());
        return HUpgradeResult.SUCCESS;
    }

    public void removeUpgrade(HRolledUpgrade upgrade) {
        upgrades.remove(upgrade);
        saveChanges();
    }

    public void removeUpgrade(HItemUpgrade upgrade) {
        upgrades.removeIf(u -> u.getUpgrade().equals(upgrade));
        saveChanges();
    }

    public List<HRolledUpgrade> getUpgrades() {
        return new ArrayList<>(upgrades); // Return a copy to prevent modification, changes won't be saved
    }

    public HItemStack update() {
        stack.applyComponents(item.getPatch());
        if (stack.getItem() != item.getBaseItem()) {
            stack.setItem(item.getBaseItem()); // Is there a better way?
        }
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("hephaestus-id", item.getKey().toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
         return this;
    }

    public void updateVisuals(Player player) {
        if (item.getNameKey() != null) {
            Locale locale = player.locale();
            Component component = Component.text(HItemTranslationRegistry.getTranslation(locale, item.getNameKey()));
            component = component.color(rarity.getColor());
            stack.set(DataComponents.ITEM_NAME, PaperAdventure.asVanilla(component));
        }
    }

    private void loadDataFromNBT() {
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe().getCompound("hephaestus-data");
            if (tag.isEmpty()) {
                return;
            }
            itemLevel = tag.getInt("level");
            rarity = HRarity.valueOf(tag.getString("rarity").toUpperCase(Locale.ROOT));
            loadUpgradesFromTag(tag);
        } else {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("hephaestus-id", item.getKey().toString());
            saveUpgradesInTag(compoundTag);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
        }
    }

    public void saveChanges() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", itemLevel);
        tag.putString("rarity", rarity.name());
        saveUpgradesInTag(tag);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }

    private void saveUpgradesInTag(CompoundTag tag) {
        for (HRolledUpgrade upgrade : upgrades) {
            tag.put(upgrade.getId(), upgrade.toNBT());
        }
    }

    private void loadUpgradesFromTag(CompoundTag tag) {
        for (String key : tag.getAllKeys()) {
            HRolledUpgrade upgrade = HRolledUpgrade.fromNBT(this, tag.getCompound(key));
            if (upgrade != null) {
                upgrades.add(upgrade);
                continue;
            }
            item.getPlugin().getLogger().warning("Failed to load upgrade with key " + key + " for item " + item.getKey());
        }
    }

    public static HItemStack getFromStack(ItemStack stack) {
        NamespacedKey key;
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            key = NamespacedKey.fromString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
        } else {
            CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe(); // Avoid copy
            key = NamespacedKey.fromString(tag.getString("hephaestus-id"));
        }
        HItem item = Hephaestus.INSTANCE.getLibrary().get(key);
        return new HItemStack(item, stack);
    }

    public static HItemStack getFromStack(org.bukkit.inventory.ItemStack stack) {
        return getFromStack(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack));
    }
}
