package de.erethon.hephaestus.items;

import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

// A HItemStack is an item in the world, and has various properties. It references a HItem, which is the base item and acts as a template for the item.
public class HItemStack {

    private HItem item;
    private int itemLevel = 0;
    private HRarity rarity = HRarity.COMMON;
    private int maxUpgrades = 0;
    private final List<HItemUpgrade> upgrades = new ArrayList<>();

    private ItemStack stack;

    public HItemStack(HItem item) {
        this.item = item;
        stack = new ItemStack(item.getBaseItem());
    }

    public HItemStack(HItem item, ItemStack stack) {
        this.stack = stack;
        this.item = item;
        loadDataFromNBT();
    }

    public HItemStack(HItem item, org.bukkit.inventory.ItemStack stack) {
        this.item = item;
        this.stack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        loadDataFromNBT();
    }

    public ItemStack getStack() {
        return stack;
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

    public void addUpgrade(HItemUpgrade upgrade) {
        upgrades.add(upgrade);
        saveChanges();
    }

    public void removeUpgrade(HItemUpgrade upgrade) {
        upgrades.remove(upgrade);
        saveChanges();
    }

    public List<HItemUpgrade> getUpgrades() {
        return new ArrayList<>(upgrades); // Return a copy to prevent modification, changes won't be saved
    }

    private void loadDataFromNBT() {
        CompoundTag tag = stack.get(DataComponents.CUSTOM_DATA).getUnsafe().getCompound("hephaestus-data");
        itemLevel = tag.getInt("level");
        rarity = HRarity.valueOf(tag.getString("rarity").toUpperCase(Locale.ROOT));
    }

    public void saveChanges() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("level", itemLevel);
        tag.putString("rarity", rarity.name());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, tag);
    }
}
