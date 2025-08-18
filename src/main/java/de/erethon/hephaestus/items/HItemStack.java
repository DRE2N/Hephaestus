package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.hephaestus.utils.HUpgradeResult;
import io.papermc.paper.adventure.PaperAdventure;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// A HItemStack is an item in the world, and has various properties. It references a HItem, which is the base item and acts as a template for the item.
public class HItemStack {

    private static final MiniMessage miniMessage = MiniMessage.miniMessage();

    private HItem item;
    private int itemLevel = 0;
    private HRarity rarity = HRarity.COMMON;
    private int maxUpgrades = 0;
    private String playerAddedFlavourText = "";
    private String playerAddedName = "";
    private final List<HRolledUpgrade> upgrades = new ArrayList<>();

    private ItemStack stack;

    public HItemStack(HItem item) {
        this.item = item;
        stack = new ItemStack(item.getBaseItem());
        update();
    }

    public HItemStack(HItem item, ItemStack stack) {
        this.stack = stack;
        this.item = item;
        loadDataFromNBT();
        if (item == null) {
            return;
        }
        update();
    }

    public HItemStack(HItem item, org.bukkit.inventory.ItemStack stack) {
        this.item = item;
        this.stack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        loadDataFromNBT();
        update();
    }

    public ItemStack getVanillaStack() {
        return stack;
    }

    public org.bukkit.inventory.ItemStack getBukkitStack() {
        stack.applyComponents(item.getPatch());
        saveChanges();
        if (!item.isVanilla()) {
            updateVisuals();
        }
        return org.bukkit.craftbukkit.inventory.CraftItemStack.asCraftMirror(stack);
    }

    public HItem getItem() {
        return item;
    }

    public void setItemLevel(int itemLevel) {
        this.itemLevel = itemLevel;
        saveChanges();
        updateVisuals();
    }

    public int getItemLevel() {
        return itemLevel;
    }

    public void setRarity(HRarity rarity) {
        this.rarity = rarity;
        saveChanges();
        updateVisuals();
    }

    public HRarity getRarity() {
        return rarity;
    }

    public void setMaxUpgrades(int maxUpgrades) {
        this.maxUpgrades = maxUpgrades;
        saveChanges();
        updateVisuals();
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
        updateVisuals();
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
        return new ArrayList<>(upgrades);
    }

    public HItemStack update() {
        if (stack.getItem() != item.getBaseItem()) {
            stack.setItem(item.getBaseItem());
        }
        stack.applyComponents(item.getPatch());
        CompoundTag custom = stack.has(DataComponents.CUSTOM_DATA)
                ? stack.get(DataComponents.CUSTOM_DATA).getUnsafe()
                : new CompoundTag();
        if (custom.getString("hephaestus-id").isEmpty()) {
            custom.putString("hephaestus-id", item.getKey().toString());
        }
        CustomData.set(DataComponents.CUSTOM_DATA, stack, custom);
        saveChanges();
        if (!item.isVanilla()) {
            updateVisuals();
        }
        return this;
    }

    public void updateVisuals(Player player) {
        if (item.isVanilla()) {
            return;
        }
        String id =  item.getKey().toString().replace(":", ".");
        net.minecraft.network.chat.Component nameComponent = net.minecraft.network.chat.Component.translatable("hephaestus.item." + id + ".name");
        if (!playerAddedName.isEmpty()) {
            Component advComponent = miniMessage.deserialize(playerAddedName);
            nameComponent = PaperAdventure.asVanilla(advComponent);
        }
        stack.set(DataComponents.ITEM_NAME, nameComponent);
        List<net.minecraft.network.chat.Component> lore = new ArrayList<>();
        if (itemLevel != 0) {
            Component verticalLine = Component.text(" | ", NamedTextColor.DARK_GRAY);
            Component header = Component.text(itemLevel, NamedTextColor.GOLD).append(verticalLine)
                    .append(Component.translatable("hephaestus.item." + id + ".category", TextColor.color(99, 99, 99)))
                    .append(verticalLine)
                    .append(Component.translatable(rarity.getTranslationKey(), rarity.getColor()));
            lore.add(PaperAdventure.asVanilla(header));
        } else {
            Component header = Component.translatable("hephaestus.item." + id + ".category")
                    .color(rarity.getColor());
            lore.add(PaperAdventure.asVanilla(header));
        }
        Component flavourText = Component.translatable("hephaestus.item." + id + ".flavour")
                .color(NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false);
        if (!playerAddedFlavourText.isEmpty()) {
            Component advComponent = miniMessage.deserialize(playerAddedFlavourText);
            flavourText = flavourText.append(Component.space()).append(advComponent);
        }
        lore.add(PaperAdventure.asVanilla(flavourText));
        lore.add(PaperAdventure.asVanilla(Component.empty()));
        for (HRolledUpgrade upgrade : upgrades) {
            lore.add(PaperAdventure.asVanilla(upgrade.getLoreLine()));
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        saveChanges();
    }

    public void updateVisuals() {
        updateVisuals(null);
    }

    private void loadDataFromNBT() {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            // Initialize minimal tag with id
            CompoundTag custom = new CompoundTag();
            custom.putString("hephaestus-id", item.getKey().toString());
            CustomData.set(DataComponents.CUSTOM_DATA, stack, custom);
            return;
        }
        CompoundTag custom = stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
        // id is managed elsewhere
        CompoundTag data = custom.getCompound("hephaestus-data").orElse(null);
        if (data == null) {
            return; // keep defaults (0/common)
        }
        itemLevel = data.getInt("level").orElse(itemLevel);
        data.getString("rarity").ifPresent(r -> {
            try { rarity = HRarity.valueOf(r.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        });
        maxUpgrades = data.getInt("maxUpgrades").orElse(maxUpgrades);
        playerAddedName = data.getString("playerAddedName").orElse(playerAddedName);
        playerAddedFlavourText = data.getString("playerAddedFlavourText").orElse(playerAddedFlavourText);
        loadUpgradesFromTag(data);
    }

    public void saveChanges() {
        CompoundTag custom;
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            custom = stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
        } else {
            custom = new CompoundTag();
        }
        if (!custom.getString("hephaestus-id").isPresent() && item != null) {
            custom.putString("hephaestus-id", item.getKey().toString());
        }
        CompoundTag data = custom.getCompound("hephaestus-data").orElse(new CompoundTag());
        data.putInt("level", itemLevel);
        data.putString("rarity", rarity.name());
        data.putInt("maxUpgrades", maxUpgrades);
        data.putString("playerAddedName", playerAddedName);
        data.putString("playerAddedFlavourText", playerAddedFlavourText);
        CompoundTag upgradesTag = new CompoundTag();
        saveUpgradesInTag(upgradesTag);
        data.put("upgrades", upgradesTag);
        custom.put("hephaestus-data", data);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, custom);
    }

    private void saveUpgradesInTag(CompoundTag tag) {
        for (HRolledUpgrade upgrade : upgrades) {
            tag.put(upgrade.getId(), upgrade.toNBT());
        }
    }

    private void loadUpgradesFromTag(CompoundTag compoundTag) {
        Optional<CompoundTag> optUpgrades = compoundTag.getCompound("upgrades");
        if (optUpgrades.isEmpty()) {
            return;
        }
        CompoundTag upgradeTag = optUpgrades.get();
        for (String key : upgradeTag.keySet()) {
            HRolledUpgrade upgrade = HRolledUpgrade.fromNBT(this, upgradeTag.getCompound(key).get());
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
            Optional<String> optId = tag.getString("hephaestus-id");
            // Fallback to vanilla key if no Hephaestus ID is present
            key = optId.map(ResourceLocation::parse).orElse(vanillaKey);
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
