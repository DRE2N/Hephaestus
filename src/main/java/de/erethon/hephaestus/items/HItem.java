package de.erethon.hephaestus.items;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Dynamic;
import com.mojang.serialization.JsonOps;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.interactions.HItemDropAction;
import de.erethon.hephaestus.items.interactions.HItemEquipAction;
import de.erethon.hephaestus.items.interactions.HItemInteractAction;
import de.erethon.hephaestus.items.interactions.HItemUnequipAction;
import de.erethon.hephaestus.items.orbs.OrbColor;
import de.erethon.hephaestus.utils.HRandom;
import de.erethon.spellbook.api.SpellData;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.craftbukkit.CraftRegistry;
import org.bukkit.craftbukkit.CraftSound;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HItem {

    private final Hephaestus plugin = Hephaestus.INSTANCE;
    private final HItemLibrary library = plugin.getLibrary();

    // Basics
    private final File file;
    private ResourceLocation key;
    private Item baseItem;
    private DataComponentPatch patch;
    private BlockData blockData = null;
    private SoundEvent placementSound = null;
    private final Set<String> allowedUpgrades = new HashSet<>();
    private final Set<String> tags = new HashSet<>();
    // Orb system
    private final List<OrbColor> socketColors = new ArrayList<>();
    private OrbColor orbColor = null; // if this item is an orb itself
    private String grantedUpgradeId = null; // upgrade granted when inserted

    // Blocks
    private float breakSpeedModifier = 1.0f;

    // Randomization
    private Map<Integer, Integer> levelWeights = new HashMap<>();
    private Map<Integer, Map<String, Integer>> rarityWeights = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> slotWeights = new HashMap<>();
    private Map<HRarity, Map<Integer, Map<String, Integer>>> socketPatternWeights = new HashMap<>();

    // Interactions
    private final Set<HItemInteractAction> interactActions = new HashSet<>();
    private final Set<HItemEquipAction> equipActions = new HashSet<>();
    private final Set<HItemUnequipAction> unequipActions = new HashSet<>();
    private final Set<HItemDropAction> dropActions = new HashSet<>();

    // Spells (mostly used for Jobs right now)
    private final Set<SpellData> rightClickSpells = new HashSet<>();

    private YamlConfiguration config = null;

    public HItem(File file) {
        this.file = file;
        load();
    }

    public HItem(ResourceLocation key, File file) {
        this.file = file;
    }

    public HItem(ResourceLocation key, Item baseItem, DataComponentPatch patch) {
        this.key = key;
        this.baseItem = baseItem;
        this.patch = sanitizePatch(patch == null ? DataComponentPatch.EMPTY : patch);
        this.file = null;
    }

    public void registerInteractAction(HItemInteractAction action) {
        interactActions.add(action);
    }

    public void registerEquipAction(HItemEquipAction action) {
        equipActions.add(action);
    }

    public void registerUnequipAction(HItemUnequipAction action) {
        unequipActions.add(action);
    }

    public void registerDropAction(HItemDropAction action) {
        dropActions.add(action);
    }

    public void runInteractActions(HItemStack stack, PlayerInteractEvent event) {
        interactActions.forEach(action -> action.onInteract(stack, event));
    }

    public void runEquipActions(HItemStack stack, PlayerArmorChangeEvent event) {
        equipActions.forEach(action -> action.onEquip(stack, event));
    }

    public void runUnequipActions(HItemStack stack, PlayerArmorChangeEvent event) {
        unequipActions.forEach(action -> action.onUnequip(stack, event));
    }

    public void runDropActions(HItemStack stack, EntityDropItemEvent event) {
        dropActions.forEach(action -> action.onDrop(stack, event));
    }

    public void runRightClickSpells(Player caster, HItemStack stack) {
        rightClickSpells.forEach(spell -> spell.getActiveSpell(caster).ready());
    }

    /**
     * Rolls a random item stack of this item, using the configured level weights and rarity weights.
     * The level is determined by the level weights, and the rarity is determined by the rarity weights for that level.
     * The socket pattern is also applied based on the rarity and level.
     * @return a new HItemStack with random properties based on this item
     */
    public HItemStack rollRandomStack() {
        return rollRandomStack(0);
    }

    /**
     * Rolls a random item stack of this item, using the configured level weights and rarity weights.
     * The level is determined by the level weights, and the rarity is determined by the rarity weights for that level.
     * The socket pattern is also applied based on the rarity and level.
     * @param minLevel the minimum level to roll, used to ensure the item is at least this level
     * @return a new HItemStack with random properties based on this item
     */
    public HItemStack rollRandomStack(int minLevel) {
        HItemStack hStack = new HItemStack(this); // visuals now auto-init
        int level = 0;
        if (levelWeights != null && !levelWeights.isEmpty()) {
            level = (int) HRandom.selectWeightedRandomValue(levelWeights, minLevel);
            hStack.setItemLevel(level);
        }
        Map<String, Integer> rarityWeightsForLevel = rarityWeights.getOrDefault(level, new HashMap<>());
        if (!rarityWeightsForLevel.isEmpty()) {
            hStack.setRarity(HRarity.valueOf(HRandom.selectWeightedRandomValue(rarityWeightsForLevel).toUpperCase(Locale.ROOT)));
        }
        Map<Integer, Integer> slotWeightsForLevel = slotWeights.getOrDefault(level, new HashMap<>());
        if (!slotWeightsForLevel.isEmpty()) {
            hStack.setMaxUpgrades(HRandom.selectWeightedRandomValue(slotWeightsForLevel));
        }
        applyRandomSocketPattern(hStack, level);
        hStack.updateVisuals();
        return hStack;
    }

    /**
     * Creates a new item stack of this item with the default amount of 1.
     * @return a new HItemStack with the default amount
     */
    public HItemStack createStack() {
        return createStack(1);
    }

    /**
     * Creates a new item stack of this item with the given amount.
     * @param amount the amount of items in the stack
     * @return a new HItemStack with the given amount
     */
    public HItemStack createStack(int amount) {
        return createStack(amount, 0);
    }

    /**
     * Creates a new item stack of this item with the given amount and level.
     * @param amount the amount of items in the stack
     * @param level the item level, used for randomization and rarity
     * @return a new HItemStack with the given parameters
     */
    public HItemStack createStack(int amount, int level) {
        return createStack(amount, level, null);
    }

    /**
     * Creates a new item stack of this item with the given parameters.
     * @param amount the amount of items in the stack
     * @param level the item level, used for randomization and rarity
     * @param socketPattern the socket pattern to apply, or null for no sockets.
     *                      Pattern: "RGB" for 3 sockets, "RRGGBB" for 6 sockets, etc.
     *                      R for red, G for green, B for blue, P for prismatic (any color)
     * @return a new HItemStack with the given parameters
     */
    public HItemStack createStack(int amount, int level, String socketPattern) {
        return createStack(amount, level, socketPattern, HRarity.COMMON);
    }

    /**
     * Creates a new item stack of this item with the given parameters.
     * @param amount the amount of items in the stack
     * @param level the item level, used for randomization and rarity
     * @param socketPattern the socket pattern to apply, or null for no sockets.
     *                      Pattern: "RGB" for 3 sockets, "RRGGBB" for 6 sockets, etc.
     *                      R for red, G for green, B for blue, P for prismatic (any color)
     * @param rarity the rarity of the item, used for randomization and socket patterns.
     * @return a new HItemStack with the given parameters
     */
    public HItemStack createStack(int amount, int level, String socketPattern, HRarity rarity) {
        HItemStack hStack = new HItemStack(this);
        hStack.update();
        hStack.setItemLevel(level);
        hStack.setRarity(rarity);
        if (socketPattern != null && !socketPattern.isBlank()) {
            hStack.applySocketPattern(socketPattern);
            hStack.setMaxUpgrades(hStack.getSockets().size());
        }
        hStack.getVanillaStack().setCount(amount);
        return hStack;
    }

    /**
     * Gets the NMS key of this item, which is a unique identifier in the format namespace:path.
     * @return the ResourceLocation key of this item
     */
    public ResourceLocation getKey() {
        return key;
    }

    /**
     * Gets the Bukkit NamespacedKey of this item, which is used for Bukkit APIs.
     * This is a conversion from the NMS ResourceLocation to the Bukkit format.
     * @return the NamespacedKey of this item
     */
    public NamespacedKey getBukkitKey() {
        return new NamespacedKey(key.getNamespace(), key.getPath());
    }

    /**
     * Gets the NMS base item this custom item is based on.
     * This is a vanilla item, and the custom item will have additional properties or modifications.
     * @return the base Item of this custom item
     */
    public Item getBaseItem() {
        return baseItem;
    }

    /**
     * Gets the block data for this item, if it has a block state (e.g. placeable items).
     * This is used to determine how the item behaves when placed in the world.
     * @return the BlockData of this item, or null if it does not have a block state
     */
    public @Nullable BlockData getBlockData() {
        return blockData;
    }

    /**
     * Sets the block data for this item, registering it in the block library.
     * This is used to define how the item behaves when placed in the world.
     * This will turn the item into a placeable item with a block state.
     * @param blockData the BlockData to set for this item
     */
    public void setBlockData(BlockData blockData) {
        this.blockData = blockData;
        plugin.getBlockLibrary().register(this, blockData);
    }

    /**
     * Gets the modifier for the break speed of blocks when this item is used.
     * This is a multiplier applied to the break speed, where 1.0 is normal speed.
     * Only applies to items with a block state
     * @return the break speed modifier
     */
    public float getBreakSpeedModifier() {
        return breakSpeedModifier;
    }

    /**
     * Tags can be used to categorize items, e.g. for filtering who can equip them.
     * @return a set of tags associated with this item
     */
    public Set<String> getTags() {
        return tags;
    }

    /**
     * Gets the colors of the orb sockets on this item.
     * If this item is an orb item, it will return the color of the orb itself.
     * If this item has no sockets, it will return an empty list.
     * @return a list of OrbColor representing the socket colors
     */
    public List<OrbColor> getSocketColors() { return socketColors; }

    /**
     * Checks if this item is an orb item.
     * An orb item has a color and a granted upgrade ID, which is applied when the orb is inserted into a socket.
     * @return true if this item is an orb item, false otherwise
     */
    public boolean isOrbItem() { return orbColor != null && grantedUpgradeId != null; }

    /**
     * Gets the color of the orb if this item is an orb item.
     * If this item is not an orb item, this will return null.
     * @return the OrbColor of the orb, or null if this item is not an orb item
     */
    public @Nullable OrbColor getOrbColor() { return orbColor; }

    /**
     * Gets the ID of the upgrade granted when this orb is inserted into a socket.
     * If this item is not an orb item, this will return null.
     * @return the ID of the granted upgrade, or null if this item is not an orb item
     */
    public @Nullable String getGrantedUpgradeId() { return grantedUpgradeId; }

    /**
     * Gets the sound played when this item is placed, if it is placeable (has a block state)
     * If no custom sound is defined, it defaults to BLOCK_STONE_PLACE.
     * @return the placement sound
     */
    public @NotNull Sound getPlacementSound() {
        if (placementSound == null) {
            return Sound.BLOCK_STONE_PLACE;
        }
        return CraftSound.minecraftToBukkit(placementSound);
    }

    public DataComponentPatch getPatch() {
        return patch == null ? DataComponentPatch.EMPTY : patch;
    }

    /**
     * Gets the allowed upgrades for this item.
     * This is a set of upgrade IDs that can be applied to this item.
     * @return a set of allowed upgrade IDs
     */
    public Set<String> getAllowedUpgrades() {
        return allowedUpgrades;
    }

    public HItemLibrary getLibrary() {
        return library;
    }

    public Hephaestus getPlugin() {
        return plugin;
    }

    /**
     * Checks if this item is a vanilla item.
     * A vanilla item has the namespace "minecraft".
     * @return true if this item is a vanilla item, false otherwise
     */
    public boolean isVanilla() {
        return key.getNamespace().equals("minecraft");
    }

    private void load() {
        config = YamlConfiguration.loadConfiguration(file);
        key = ResourceLocation.parse(config.getString("key", "hephaestus:default"));
        if (BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.getString("baseItem", "minecraft:stone"))).isPresent()) {
            baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.getString("baseItem", "minecraft:stone"))).get().value();
        } else {
            throw new RuntimeException("Base item not found: " + config.getString("baseItem"));
        }
        if (config.contains("name")) {
            ConfigurationSection nameSection = config.getConfigurationSection("name");
            if (nameSection != null) {
                for (String key : nameSection.getKeys(false)) {
                    Locale locale;
                    if (key.contains("de")) {
                        locale = Locale.GERMANY;
                    } else {
                        locale = Locale.US;
                    }
                    String id =  this.key.toString().replace(":", ".");
                    plugin.registerTranslation("hephaestus.item." + id + ".name", locale, config.getString("name." + key));
                }
            }
        }
        if (config.contains("category")) {
            ConfigurationSection categorySection = config.getConfigurationSection("category");
            if (categorySection != null) {
                for (String key : categorySection.getKeys(false)) {
                    Locale locale;
                    if (key.contains("de")) {
                        locale = Locale.GERMANY;
                    } else {
                        locale = Locale.US;
                    }
                    String id = this.key.toString().replace(":", ".");
                    plugin.registerTranslation("hephaestus.item." + id + ".category", locale, config.getString("category." + key));
                }
            }
        }
        if (config.contains("flavour")) {
            ConfigurationSection descriptionSection = config.getConfigurationSection("flavour");
            if (descriptionSection != null) {
                for (String key : descriptionSection.getKeys(false)) {
                    Locale locale;
                    if (key.contains("de")) {
                        locale = Locale.GERMANY;
                    } else {
                        locale = Locale.US;
                    }
                    String id =  this.key.toString().replace(":", ".");
                    plugin.registerTranslation("hephaestus.item." + id + ".flavour", locale, config.getString("flavour." + key));
                }
            }
        }

        if (config.contains("patch")) {
            try {
                patch = deserialize(config.getString("patch"));
                patch = sanitizePatch(patch);
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading patch for item " + key + ": " + e.getMessage() + ", Data: " + config.getString("patch"));
                return;
            }
        }

        if (config.contains("placedBlockData")) {
            blockData = Bukkit.getServer().createBlockData(config.getString("placedBlockData"));
            plugin.getBlockLibrary().register(this, blockData);
        }
        if (config.contains("breakSpeedModifier")) {
            breakSpeedModifier = (float) config.getDouble("breakSpeedModifier", 1.0f);
        }
        if (config.contains("placementSound")) {
            placementSound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(config.getString("placementSound", "minecraft:block.stone.place"))).get().value();
        }
        if (config.contains("allowedUpgrades")) {
            List<String> allowedUpgrades = config.getStringList("allowedUpgrades");
            this.allowedUpgrades.addAll(allowedUpgrades);
        }
        if (config.contains("tags")) {
            List<String> tags = config.getStringList("tags");
            this.tags.addAll(tags);
        }
        // Orb sockets on equipment
        if (config.contains("sockets")) {
            for (String s : config.getStringList("sockets")) {
                OrbColor c = OrbColor.fromString(s);
                if (c != null) socketColors.add(c);
            }
        }
        // Orb item definition
        if (config.contains("orbColor")) {
            orbColor = OrbColor.fromString(config.getString("orbColor"));
        }
        if (config.contains("grantedUpgrade")) {
            grantedUpgradeId = config.getString("grantedUpgrade");
        }
        if (isOrbItem()) {
            tags.add("orb");
        }

        levelWeights = HRandom.loadWeights(config, "random.level");
        if (!levelWeights.isEmpty()) {
            plugin.getLogger().info("Loaded " + levelWeights.size() + " level weights for item " + key);
        }
        // Load level-specific rarity and slot weights
        if (config.contains("random.rarity")) {
            var levelSpecificRaritySection = config.getConfigurationSection("random.rarity");
            for (String levelKey : levelSpecificRaritySection.getKeys(false)) {
                int level = Integer.parseInt(levelKey);
                rarityWeights.put(level, HRandom.loadWeights(config, "random.rarity." + levelKey));
            }
        }
        if (config.contains("random.slots")) {
            var levelSpecificSlotsSection = config.getConfigurationSection("random.slots");
            for (String levelKey : levelSpecificSlotsSection.getKeys(false)) {
                int level = Integer.parseInt(levelKey);
                slotWeights.put(level, HRandom.loadWeights(config, "random.slots." + levelKey));
            }
        }
        if (config.contains("random.socketPatterns")) {
            ConfigurationSection spRoot = config.getConfigurationSection("random.socketPatterns");
            for (String rarityKey : spRoot.getKeys(false)) {
                HRarity r;
                try { r = HRarity.valueOf(rarityKey.toUpperCase(Locale.ROOT)); } catch (Exception ex) { continue; }
                Map<Integer, Map<String,Integer>> perLevel = new HashMap<>();
                ConfigurationSection raritySection = spRoot.getConfigurationSection(rarityKey);
                for (String levelKey : raritySection.getKeys(false)) {
                    int lvl;
                    try { lvl = Integer.parseInt(levelKey); } catch (NumberFormatException ex) { continue; }
                    Map<String,Integer> weights = HRandom.loadWeights(config, "random.socketPatterns."+rarityKey+"."+levelKey);
                    // loadWeights returns Map<String,Integer> but uses generic; ensure only string keys kept
                    perLevel.put(lvl, new HashMap<>(weights));
                }
                socketPatternWeights.put(r, perLevel);
            }
        }
        if (config.contains("rightClickSpells")) {
            List<String> spells = config.getStringList("rightClickSpells");
            for (String spell : spells) {
                SpellData spellData = Bukkit.getServer().getSpellbookAPI().getLibrary().getSpellByID(spell);
                if (spellData != null) {
                    rightClickSpells.add(spellData);
                } else {
                    plugin.getLogger().warning("Spell not found: " + spell + " for item " + key);
                }
            }
            plugin.getLogger().info("Loaded " + rightClickSpells.size() + " right-click spells for item " + key);
        }
        if (patch == null) {
            patch = DataComponentPatch.EMPTY;
        }
    }

    public void save(File file) {
        config.set("key", key.toString());
        config.setComments("key", List.of("The key of the item. This is used to identify the item in the game, e.g. in /give",
                "This should be unique and follow the format namespace:path.",
                "Our regular custom items use the namespace 'erethon'."));
        config.set("baseItem", BuiltInRegistries.ITEM.getKey(baseItem).toString());
        config.setComments("baseItem", List.of("The base item this custom item is based on. If this is a vanilla item, this will be the vanilla item."));
        try {
            if (patch == null) {
                patch = DataComponentPatch.EMPTY;
            }
            DataComponentMap defaultComponents = baseItem.components();
            DataComponentPatch.Builder defaultBuilder = DataComponentPatch.builder();
            for (TypedDataComponent component : defaultComponents) {
                defaultBuilder.set(component.type(), defaultComponents.get(component.type()));
            }
            DataComponentPatch patchToSave = patch;
            if (!key.getNamespace().equals("minecraft")) {
                CompoundTag tag = new CompoundTag();
                tag.putString("hephaestus-id", key.toString());
                DataComponentPatch.Builder tmp = DataComponentPatch.builder();
                tmp.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                tmp.copy(patch); // original patch contents
                patchToSave = tmp.build();
            }
            DataComponentPatch defaultPatch = defaultBuilder.build();
            config.set("vanilla", serialize(defaultPatch));
            config.set("patch", serialize(patchToSave));
            config.setComments("patch", List.of("Our patch, as JSON. This is used to actually modify the item",
                    "See https://minecraft.wiki/w/Data_component_format for all possible components.",
                    "Components here will override vanilla components."));
        } catch (Exception e) {
            plugin.getLogger().warning("Error saving patch for item " + key + ": " + e.getMessage() + ", Data: " + patch + ", Default: " + baseItem.components());
        }
        if (blockData != null) {
            config.set("placedBlockData", blockData.getAsString());
        }
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String serialize(DataComponentPatch patch) {
        RegistryOps<JsonElement> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(JsonOps.INSTANCE);
        net.minecraft.world.item.component.CustomData.SERIALIZE_CUSTOM_AS_SNBT.set(true); // This is needed for custom components. Vanilla deserialize will handle it fine
        DataResult<JsonElement> result;
        try {
            result = DataComponentPatch.CODEC.encodeStart(ops, patch);
        } finally {
            net.minecraft.world.item.component.CustomData.SERIALIZE_CUSTOM_AS_SNBT.set(false);
        }
        if (result.error().isPresent()) {
            throw new RuntimeException("Error serializing DataComponentPatch: " + result.error().get().message());
        }
        JsonObject tag = result.result().get().getAsJsonObject();
        // Add the current data version, so DFU can update the component if needed
        tag.addProperty("DataVersion", SharedConstants.getCurrentVersion().dataVersion().version());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(tag);
    }

    public static DataComponentPatch deserialize(String string) {
        JsonObject element = JsonParser.parseString(string).getAsJsonObject();
        int dataVersion = element.get("DataVersion").getAsInt();
        int currentVersion = SharedConstants.getCurrentVersion().dataVersion().version();
        element = (JsonObject) MinecraftServer.getServer().fixerUpper.update(References.DATA_COMPONENTS, new Dynamic(JsonOps.INSTANCE, element), dataVersion, currentVersion).getValue();
        RegistryOps<JsonElement> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(JsonOps.INSTANCE);
        element.remove("DataVersion"); // This is not a component, so we can't deserialize it
        return DataComponentPatch.CODEC.decode(ops, element).getOrThrow().getFirst();
    }

    private DataComponentPatch sanitizePatch(DataComponentPatch original) {
        if (original == null || original == DataComponentPatch.EMPTY) {
            return original;
        }
        boolean hasCustom = original.entrySet().stream().anyMatch(e -> e.getKey() == DataComponents.CUSTOM_DATA);
        if (!hasCustom) {
            return original;
        }
        DataComponentPatch sanitized = original.forget(type -> type == DataComponents.CUSTOM_DATA);
        plugin.getLogger().fine("Stripped CUSTOM_DATA from patch for " + key);
        return sanitized;
    }

    private void applyRandomSocketPattern(HItemStack stack, int level) {
        if (socketPatternWeights.isEmpty()) return;
        HRarity rarity = stack.getRarity();
        Map<Integer, Map<String, Integer>> byLevel = socketPatternWeights.get(rarity);
        if (byLevel == null || byLevel.isEmpty()) return;
        // find direct or nearest lower level definition
        Map<String,Integer> patternMap = byLevel.get(level);
        if (patternMap == null) {
            int best = Integer.MIN_VALUE;
            for (Integer defined : byLevel.keySet()) {
                if (defined <= level && defined > best) best = defined;
            }
            if (best != Integer.MIN_VALUE) patternMap = byLevel.get(best);
        }
        if (patternMap == null || patternMap.isEmpty()) return;
        String pattern = HRandom.selectWeightedRandomValue(patternMap);
        if (pattern == null || pattern.isBlank()) return;
        stack.applySocketPattern(pattern);
        stack.setMaxUpgrades(stack.getSockets().size());
    }


}
