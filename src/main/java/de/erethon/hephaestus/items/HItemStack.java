package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.hephaestus.utils.HUpgradeResult;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;

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
        if (item == null) { // Handle deleted items
            return;
        }
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
        if (!item.getAllowedUpgrades().contains(id) && !item.getAllowedUpgrades().isEmpty()) {
            return HUpgradeResult.INVALID_ITEM;
        }
        if (upgrade.getValidItems().stream().noneMatch(i -> i.equals(item.getBukkitKey()))  && !upgrade.getValidItems().isEmpty()) {
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
        if (!hasRequired && !upgrade.getRequiredUpgrades().isEmpty()) {
            return HUpgradeResult.MISSING_REQUIRED_UPGRADE;
        }
        boolean hasIncompatible = false;
        for (String incompatible : upgrade.getIncompatibleUpgrades()) {
            if (upgrades.stream().anyMatch(u -> u.getUpgrade().getId().equals(incompatible))) {
                hasIncompatible = true;
                break;
            }
        }
        if (hasIncompatible && !upgrade.getIncompatibleUpgrades().isEmpty()) {
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
        CompoundTag compoundTag;
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            compoundTag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
        } else {
            compoundTag = new CompoundTag();
        }
        compoundTag.putString("hephaestus-id", item.getKey().toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
        return this;
    }

    public void updateVisuals(Player player) {
        if (item.isVanilla()) {
            return;
        }
        String id =  item.getKey().toString().replace(":", ".");
        stack.set(DataComponents.ITEM_NAME, net.minecraft.network.chat.Component.translatable("hephaestus.item." + id + ".name"));
        for (HRolledUpgrade upgrade : upgrades) {
            stack.set(DataComponents.ITEM_NAME,net.minecraft.network.chat.Component.translatable("hephaestus.upgrade." + upgrade.getId() + ".name"));
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
            maxUpgrades = tag.getInt("maxUpgrades");
            loadUpgradesFromTag(tag);
        } else {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.putString("hephaestus-id", item.getKey().toString());
            CompoundTag upgradesTag = new CompoundTag();
            //saveUpgradesInTag(upgradesTag);
            //compoundTag.put("upgrades", upgradesTag);
            CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
        }
    }

    public void saveChanges() {
        CompoundTag tag = new CompoundTag();
        CompoundTag customData = stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
        tag.putInt("level", itemLevel);
        tag.putString("rarity", rarity.name());
        tag.putInt("maxUpgrades", maxUpgrades);
        CompoundTag upgradesTag = new CompoundTag();
        saveUpgradesInTag(upgradesTag);
        tag.put("upgrades", upgradesTag);
        customData.put("hephaestus-data", tag);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, customData);
    }

    private void saveUpgradesInTag(CompoundTag tag) {
        for (HRolledUpgrade upgrade : upgrades) {
            tag.put(upgrade.getId(), upgrade.toNBT());
        }
    }

    private void loadUpgradesFromTag(CompoundTag compoundTag) {
        CompoundTag upgradeTag = compoundTag.getCompound("upgrades");
        for (String key : upgradeTag.getAllKeys()) {
            HRolledUpgrade upgrade = HRolledUpgrade.fromNBT(this, upgradeTag.getCompound(key));
            if (upgrade != null) {
                upgrades.add(upgrade);
                continue;
            }
            item.getPlugin().getLogger().warning("Failed to load upgrade with key " + key + " for item " + item.getKey());
        }
    }

    public static HItemStack getFromStack(ItemStack stack) {
        ResourceLocation key;
        ResourceLocation vanillaKey = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            key = vanillaKey;
        } else {
            CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe(); // Avoid copy
            key = ResourceLocation.parse(tag.getString("hephaestus-id"));
        }
        HItem item = Hephaestus.INSTANCE.getLibrary().get(key);
        if (item == null) { // If deleted, return vanilla item
            item = Hephaestus.INSTANCE.getLibrary().get(vanillaKey);
        }
        return new HItemStack(item, stack);
    }

    public static HItemStack getFromStack(org.bukkit.inventory.ItemStack stack) {
        return getFromStack(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack));
    }
}
