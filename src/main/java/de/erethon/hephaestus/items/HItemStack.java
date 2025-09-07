package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.orbs.OrbColor;
import de.erethon.hephaestus.items.orbs.OrbSocket;
import de.erethon.hephaestus.items.upgrades.HAttributeModifyingUpgrade;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;
import de.erethon.hephaestus.utils.HUpgradeResult;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.entity.EquipmentSlotGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Map;

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
    private final List<OrbSocket> sockets = new ArrayList<>();

    private ItemStack stack;

    public HItemStack(HItem item) {
        this.item = item;
        stack = new ItemStack(item.getBaseItem());
        initSockets();
        update();
    }

    public HItemStack(HItem item, ItemStack stack) {
        this.stack = stack;
        this.item = item;
        initSockets();
        loadDataFromNBT();
        if (item == null) {
            return;
        }
        reconcileSocketUpgrades();
        update();
    }

    public HItemStack(HItem item, org.bukkit.inventory.ItemStack stack) {
        this.item = item;
        this.stack = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack);
        initSockets();
        loadDataFromNBT();
        reconcileSocketUpgrades();
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
        rebuildAttributes();
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

    private void initSockets() {
        sockets.clear();
        if (item == null) return;
        for (OrbColor c : item.getSocketColors()) {
            sockets.add(new OrbSocket(c));
        }
    }

    public void applySocketPattern(String pattern) {
        sockets.clear();
        if (pattern == null) return;
        pattern = pattern.trim();
        if (pattern.isEmpty()) return;
        // Pattern may be comma or space separated tokens or just letters. Accept formats like "RBG", "R,B,G", "RED BLUE PRISMATIC"
        String normalized = pattern.replace(",", " ").replace("-", " ");
        if (normalized.matches("^[A-Za-z]+$") && !normalized.contains(" ")) {
            for (char ch : normalized.toCharArray()) {
                OrbColor c = switch (Character.toUpperCase(ch)) {
                    case 'R' -> OrbColor.RED;
                    case 'B' -> OrbColor.BLUE;
                    case 'G' -> OrbColor.GREEN;
                    case 'P' -> OrbColor.PRISMATIC;
                    default -> null;
                };
                if (c != null) sockets.add(new OrbSocket(c));
            }
        } else {
            for (String token : normalized.split(" ")) {
                if (token.isBlank()) continue;
                OrbColor c = OrbColor.fromString(token);
                if (c != null) sockets.add(new OrbSocket(c));
            }
        }
        saveChanges();
    }

    private void reconcileSocketUpgrades() {
        for (HRolledUpgrade u : upgrades) {
            int idx = u.getSocketIndex();
            if (idx >= 0 && idx < sockets.size()) {
                sockets.get(idx).setInserted(u);
            }
        }
    }

    public List<OrbSocket> getSockets() { return new ArrayList<>(sockets); }

    public boolean hasSockets() { return !sockets.isEmpty(); }

    public HUpgradeResult insertOrb(HItemStack orbStack) {
        if (orbStack == null || orbStack.getItem() == null || !orbStack.getItem().isOrbItem()) {
            return HUpgradeResult.INVALID_UPGRADE;
        }
        if (!hasSockets()) {
            return HUpgradeResult.INVALID_ITEM;
        }
        // Rarity / level gating: orb may only be inserted into item of >= rarity and >= level
        if (orbStack.getItemLevel() > this.itemLevel) {
            return HUpgradeResult.TOO_LOW_LEVEL; // item too low level for orb
        }
        if (orbStack.getRarity().ordinal() > this.rarity.ordinal()) {
            return HUpgradeResult.TOO_BAD_RARITY; // higher rarity orb
        }
        OrbColor orbColor = orbStack.getItem().getOrbColor();
        String upgradeId = orbStack.getItem().getGrantedUpgradeId();
        if (upgradeId == null) {
            return HUpgradeResult.INVALID_UPGRADE;
        }
        // Find matching socket
        int firstColorMismatchSocket = -1;
        for (int i = 0; i < sockets.size(); i++) {
            OrbSocket s = sockets.get(i);
            if (!s.isEmpty()) continue;
            boolean colorMatch = s.getColor() == OrbColor.PRISMATIC || orbColor == OrbColor.PRISMATIC || s.getColor() == orbColor;
            if (colorMatch) {
                // Pass orb level so upgrade rolls at orb's level
                HUpgradeResult res = addUpgradeToSocket(upgradeId, i, orbStack.getItemLevel());
                return res;
            } else if (firstColorMismatchSocket == -1) {
                firstColorMismatchSocket = i;
            }
        }
        if (firstColorMismatchSocket != -1) {
            return HUpgradeResult.SOCKET_COLOR_MISMATCH;
        }
        return HUpgradeResult.NO_EMPTY_SOCKET;
    }

    private HUpgradeResult addUpgradeToSocket(String upgradeId, int socketIndex, int sourceLevel) {
        if (socketIndex < 0 || socketIndex >= sockets.size()) return HUpgradeResult.INVALID_ITEM;
        OrbSocket socket = sockets.get(socketIndex);
        if (!socket.isEmpty()) return HUpgradeResult.INVALID_ITEM;
        HItemUpgrade upgrade = item.getPlugin().getLibrary().getUpgrade(upgradeId);
        if (upgrade == null) return HUpgradeResult.INVALID_UPGRADE;
        if (upgrade.getMinimumLevel() > itemLevel) { // still gate by target item level
            return HUpgradeResult.TOO_LOW_LEVEL;
        }
        for (String req : upgrade.getRequiredUpgrades()) {
            if (upgrades.stream().noneMatch(u -> u.getId().equals(req))) {
                return HUpgradeResult.MISSING_REQUIRED_UPGRADE;
            }
        }
        for (String inc : upgrade.getIncompatibleUpgrades()) {
            if (upgrades.stream().anyMatch(u -> u.getId().equals(inc))) {
                return HUpgradeResult.INCOMPATIBLE_UPGRADE;
            }
        }
        // Roll using the provided source level (orb's level)
        HRolledUpgrade rolled = upgrade.rollAtLevel(this, sourceLevel);
        if (rolled == null) return HUpgradeResult.INVALID_UPGRADE;
        rolled.setSocketIndex(socketIndex);
        upgrades.add(rolled);
        socket.setInserted(rolled);
        saveChanges();
        rebuildAttributes();
        updateVisuals();
        return HUpgradeResult.SUCCESS;
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
        rebuildAttributes();
        saveChanges();
        if (!item.isVanilla()) {
            updateVisuals();
        }
        return this;
    }

    public void updateVisuals() {
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
            Component header = Component.text("Level " + itemLevel, NamedTextColor.GOLD).append(verticalLine)
                    .append(Component.translatable("hephaestus.item." + id + ".category", TextColor.color(124, 124, 124)))
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

        // Orb item preview (color + granted upgrade attribute ranges)
        if (item.isOrbItem()) {
            lore.add(PaperAdventure.asVanilla(Component.empty()));
            // Color line
            lore.add(PaperAdventure.asVanilla(Component.translatable("hephaestus.upgrade.color", NamedTextColor.DARK_GRAY)
                    .append(Component.text(item.getOrbColor().getDisplayName(), item.getOrbColor().getTextColor()))));
            String upgradeId = item.getGrantedUpgradeId();
            if (upgradeId != null) {
                var up = item.getPlugin().getLibrary().getUpgrade(upgradeId);
                if (up instanceof HAttributeModifyingUpgrade attr) {
                    var def = attr.getAttributeDefinition();
                    if (!def.isEmpty()) {
                        lore.add(PaperAdventure.asVanilla(Component.translatable("hephaestus.upgrade.grants", NamedTextColor.DARK_GRAY)));
                        for (var entry : def.entrySet()) {
                            var holderKeyOpt = entry.getKey().unwrapKey();
                            if (holderKeyOpt.isEmpty()) continue;
                            String keyStr = holderKeyOpt.get().location().toString();
                            // Determine level-relevant modifiers (exact or nearest lower) like roll() does
                            var perLevel = entry.getValue();
                            Map<Double, Integer> levelModifiers = perLevel.get(itemLevel);
                            if (levelModifiers == null) {
                                int best = Integer.MIN_VALUE;
                                for (Integer defined : perLevel.keySet()) {
                                    if (defined <= itemLevel && defined > best) best = defined;
                                }
                                if (best != Integer.MIN_VALUE) {
                                    levelModifiers = perLevel.get(best);
                                }
                            }
                            if (levelModifiers == null || levelModifiers.isEmpty()) continue;
                            double min = Double.POSITIVE_INFINITY;
                            double max = Double.NEGATIVE_INFINITY;
                            for (Double v : levelModifiers.keySet()) {
                                if (v < min) min = v;
                                if (v > max) max = v;
                            }
                            if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) continue;
                            String display = getAttributeTranslationKey(keyStr);
                            boolean single = Math.abs(min - max) < 1e-9;
                            String rangeNumber = single ? formatNumber(min) : (formatNumber(min) + "-" + formatNumber(max));
                            boolean negativeRange = max <= 0; // whole range non-positive
                            NamedTextColor numberColor = negativeRange ? NamedTextColor.RED : NamedTextColor.GREEN;
                            String prefix = negativeRange ? "" : "+"; // only prepend + for positive-only ranges
                            Component line = Component.text("● ", item.getOrbColor().getTextColor())
                                    .append(Component.text(prefix + rangeNumber + " ", numberColor))
                                    .append(Component.translatable(display, NamedTextColor.GRAY));
                            lore.add(PaperAdventure.asVanilla(line));
                        }
                    }
                }
            }
            stack.set(DataComponents.LORE, new ItemLore(lore));
            saveChanges();
            return; // Do not render sockets for orb items
        }

        // Sockets section (equipment only)
        if (hasSockets()) {
            lore.add(PaperAdventure.asVanilla(Component.empty()));
            for (int i = 0; i < sockets.size(); i++) {
                OrbSocket socket = sockets.get(i);
                if (socket.isEmpty()) {
                    Component emptyLine = Component.text("○ ", socket.getColor().getTextColor())
                            .append(Component.translatable("hephaestus.upgrade.empty", NamedTextColor.DARK_GRAY));
                    lore.add(PaperAdventure.asVanilla(emptyLine));
                    continue;
                }
                var values = socket.getInserted().getValues();
                List<String> keys = new ArrayList<>(values.keySet());
                keys.sort(String::compareTo);
                boolean firstAttr = true;
                for (String k : keys) {
                    if (!values.contains(k)) continue;
                    double v = values.getDouble(k).get();
                    String display = getAttributeTranslationKey(k);
                    String numberStr = formatNumber(v);
                    boolean negative = v < 0;
                    NamedTextColor numberColor = negative ? NamedTextColor.RED : NamedTextColor.GREEN;
                    String prefix = negative ? "" : "+";
                    if (firstAttr) {
                        Component line = Component.text("● ", socket.getColor().getTextColor())
                                .append(Component.text(prefix + numberStr + " ", numberColor))
                                .append(Component.translatable(display, NamedTextColor.GRAY));
                        lore.add(PaperAdventure.asVanilla(line));
                        firstAttr = false;
                    } else {
                        Component line = Component.text("  ", NamedTextColor.GRAY) // Needs three spaces here for alignment
                                .append(Component.text(prefix + numberStr + " ", numberColor))
                                .append(Component.translatable(display, NamedTextColor.GRAY));
                        lore.add(PaperAdventure.asVanilla(line));
                    }
                }
                if (firstAttr) { // no attributes rolled
                    lore.add(PaperAdventure.asVanilla(Component.text("● ", socket.getColor().getTextColor())
                            .append(Component.text("<orb>", NamedTextColor.DARK_GRAY))));
                }
            }
        }
        stack.set(DataComponents.LORE, new ItemLore(lore));
        saveChanges();
    }

    private void loadDataFromNBT() {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            CompoundTag custom = new CompoundTag();
            custom.putString("hephaestus-id", item.getKey().toString());
            CustomData.set(DataComponents.CUSTOM_DATA, stack, custom);
            return;
        }
        CompoundTag custom = stack.get(DataComponents.CUSTOM_DATA).getUnsafe();
        CompoundTag data = custom.getCompoundOrEmpty("hephaestus-data");
        if (data == null) {
            return;
        }
        Optional<Integer> optLevel = data.getInt("level");
        if (optLevel.isEmpty()) {
            return;
        }
        itemLevel = optLevel.get();
        String rarityStr = data.getString("rarity").orElse(null);
        if (rarityStr != null && !rarityStr.isEmpty()) {
            try { rarity = HRarity.valueOf(rarityStr.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        }
        maxUpgrades = data.getInt("maxUpgrades").get();
        String n = data.getString("playerAddedName").get();
        if (n != null) playerAddedName = n;
        String f = data.getString("playerAddedFlavourText").get();
        if (f != null) playerAddedFlavourText = f;
        // Load socket pattern if present
        if (data.contains("sockets")) {
            sockets.clear();
            ListTag list = data.getList("sockets").get();
            for (int i = 0; i < list.size(); i++) {
                String col = list.getString(i).get();
                OrbColor oc = OrbColor.fromString(col);
                if (oc != null) sockets.add(new OrbSocket(oc));
            }
        }
        loadUpgradesFromTag(data);
    }

    public void saveChanges() {
        CompoundTag custom = stack.has(DataComponents.CUSTOM_DATA) ? stack.get(DataComponents.CUSTOM_DATA).getUnsafe() : new CompoundTag();
        if (!custom.contains("hephaestus-id") && item != null) {
            custom.putString("hephaestus-id", item.getKey().toString());
        }
        CompoundTag data = custom.getCompoundOrEmpty("hephaestus-data");
        data.putInt("level", itemLevel);
        data.putString("rarity", rarity.name());
        data.putInt("maxUpgrades", maxUpgrades);
        data.putString("playerAddedName", playerAddedName == null ? "" : playerAddedName);
        data.putString("playerAddedFlavourText", playerAddedFlavourText == null ? "" : playerAddedFlavourText);
        // Persist sockets
        var list = new net.minecraft.nbt.ListTag();
        for (OrbSocket s : sockets) list.add(net.minecraft.nbt.StringTag.valueOf(s.getColor().name()));
        data.put("sockets", list);
        CompoundTag upgradesTag = new CompoundTag();
        saveUpgradesInTag(upgradesTag);
        data.put("upgrades", upgradesTag);
        custom.put("hephaestus-data", data);
        CustomData.set(DataComponents.CUSTOM_DATA, stack, custom);
    }

    private void saveUpgradesInTag(CompoundTag tag) {
        for (int i = 0; i < upgrades.size(); i++) {
            HRolledUpgrade upgrade = upgrades.get(i);
            String key = upgrade.getId() + "#" + i;
            tag.put(key, upgrade.toNBT());
        }
    }

    private void loadUpgradesFromTag(CompoundTag compoundTag) {
        Optional<CompoundTag> optUpgrades = compoundTag.getCompound("upgrades");
        if (optUpgrades.isEmpty()) {
            return;
        }
        CompoundTag upgradeTag = optUpgrades.get();
        for (String key : upgradeTag.keySet()) {
            String rawId = key;
            int hash = rawId.indexOf('#');
            if (hash != -1) rawId = rawId.substring(0, hash);
            HRolledUpgrade upgrade = HRolledUpgrade.fromNBT(this, upgradeTag.getCompound(key).get());
            if (upgrade != null) {
                upgrades.add(upgrade);
                continue;
            }
            item.getPlugin().getLogger().warning("Failed to load upgrade with key " + key + " for item " + item.getKey());
        }
    }

    private void rebuildAttributes() {
        if (item == null || item.isVanilla()) return;
        ItemAttributeModifiers.Builder builder = ItemAttributeModifiers.builder();
        int uniqueCounter = 0; // ensure unique modifier IDs when multiple of same attribute
        for (HRolledUpgrade u : upgrades) {
            if (u.getValues() == null || u.getValues().isEmpty()) continue;
            for (String k : u.getValues().keySet()) {
                double val = u.getValues().getDouble(k).get();
                try {
                    ResourceLocation rl = ResourceLocation.parse(k);
                    var holderOpt = BuiltInRegistries.ATTRIBUTE.get(rl);
                    if (holderOpt.isEmpty()) continue; // unknown attribute
                    String pathPart = u.getId() + "-" + rl.getNamespace() + "_" + rl.getPath() + "-orb";
                    if (u.getSocketIndex() >= 0) {
                        pathPart += "-s" + u.getSocketIndex();
                    }
                    pathPart += "-" + uniqueCounter++;
                    ResourceLocation modifierId = ResourceLocation.tryParse("hephaestus:" + pathPart.toLowerCase(Locale.ROOT));
                    if (modifierId == null) {
                        Hephaestus.INSTANCE.getLogger().warning("Invalid generated modifier id path for upgrade " + u.getId() + ": " + pathPart);
                        continue;
                    }
                    AttributeModifier modifier = new AttributeModifier(modifierId, val, AttributeModifier.Operation.ADD_VALUE);
                    builder.add(holderOpt.get(), modifier, EquipmentSlotGroup.ANY);
                } catch (Exception ignored) {}
            }
        }
        stack.set(DataComponents.ATTRIBUTE_MODIFIERS, builder.build());
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

    private static String formatNumber(double d) {
        if (Math.abs(d - Math.rint(d)) < 1e-6) {
            return Integer.toString((int) Math.rint(d));
        }
        return String.format("%.2f", d);
    }

    private static String getAttributeTranslationKey(String key) {
        String translation =  "hephaestus.attribute." + key.replace(':', '.').replace('.', '_')
                .toLowerCase(Locale.ROOT).replace(" ", "_") +
                ".name";
        translation = translation.replace("minecraft_", "");
        return translation;
    }
}
