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
import de.erethon.hephaestus.utils.HRandom;
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
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
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

    // Blocks
    private float breakSpeedModifier = 1.0f;

    // Randomization
    private Map<Integer, Integer> levelWeights = new HashMap<>();
    private Map<Integer, Map<String, Integer>> rarityWeights = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> slotWeights = new HashMap<>();

    // Interactions
    private final Set<HItemInteractAction> interactActions = new HashSet<>();
    private final Set<HItemEquipAction> equipActions = new HashSet<>();
    private final Set<HItemUnequipAction> unequipActions = new HashSet<>();
    private final Set<HItemDropAction> dropActions = new HashSet<>();

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

    public HItemStack rollRandomStack() {
        return rollRandomStack(0);
    }

    public HItemStack rollRandomStack(int minLevel) {
        HItemStack hStack = new HItemStack(this); // visuals now auto-init
        int level = 0;
        if (levelWeights != null && !levelWeights.isEmpty()) {
            level = HRandom.selectWeightedRandomValue(levelWeights, minLevel);
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
        hStack.updateVisuals(); // ensure final display reflects randomized data
        return hStack;
    }

    public HItemStack createStack() {
        return createStack(1);
    }

    public HItemStack createStack(int amount) {
        return createStack(amount, 0);
    }

    public  HItemStack createStack(int amount, int level) {
        return createStack(amount, level, 0);
    }

    public HItemStack createStack(int amount, int level, int maxUpgrades) {
        return createStack(amount, level, maxUpgrades, HRarity.COMMON);
    }

    public HItemStack createStack(int amount, int level, int maxUpgrades, HRarity rarity) {
        HItemStack hStack = new HItemStack(this);
        hStack.update();
        hStack.setItemLevel(level);
        hStack.setMaxUpgrades(maxUpgrades);
        hStack.setRarity(rarity);
        hStack.getVanillaStack().setCount(amount);
        return hStack;
    }

    public ResourceLocation getKey() {
        return key;
    }

    public NamespacedKey getBukkitKey() {
        return new NamespacedKey(key.getNamespace(), key.getPath());
    }

    public Item getBaseItem() {
        return baseItem;
    }

    public BlockData getBlockData() {
        return blockData;
    }

    public void setBlockData(BlockData blockData) {
        this.blockData = blockData;
        plugin.getBlockLibrary().register(this, blockData);
    }

    public float getBreakSpeedModifier() {
        return breakSpeedModifier;
    }

    public Set<String> getTags() {
        return tags;
    }

    public boolean hasTag(String tag) {
        return tags.contains(tag);
    }

    public void addTag(String tag) {
        tags.add(tag);
    }

    public void removeTag(String tag) {
        tags.remove(tag);
    }

    public @NotNull Sound getPlacementSound() {
        if (placementSound == null) {
            return Sound.BLOCK_STONE_PLACE;
        }
        return CraftSound.minecraftToBukkit(placementSound);
    }

    public DataComponentPatch getPatch() {
        return patch == null ? DataComponentPatch.EMPTY : patch;
    }

    public Set<String> getAllowedUpgrades() {
        return allowedUpgrades;
    }

    public HItemLibrary getLibrary() {
        return library;
    }

    public Hephaestus getPlugin() {
        return plugin;
    }

    public boolean isVanilla() {
        return key.getNamespace().equals("minecraft");
    }

    private void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
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
                patch = sanitizePatch(patch); // ensure no CUSTOM_DATA kept
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
        levelWeights = HRandom.loadWeights(config, "random.level");
        plugin.getLogger().info("Loaded " + levelWeights.size() + " level weights for item " + key);
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
        // Ensure patch not null after load
        if (patch == null) {
            patch = DataComponentPatch.EMPTY;
        }
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
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
            // Build a SERIALIZATION-ONLY variant including CUSTOM_DATA (do NOT mutate field 'patch')
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
            // Use patchToSave (with id) for file output; runtime 'patch' stays sanitized (no CUSTOM_DATA)
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
        config.createSection("random.level", levelWeights);
        for (Map.Entry<Integer, Map<String, Integer>> entry : rarityWeights.entrySet()) {
            config.createSection("random.rarity." + entry.getKey(), entry.getValue());
        }
        for (Map.Entry<Integer, Map<Integer, Integer>> entry : slotWeights.entrySet()) {
            config.createSection("random.slots." + entry.getKey(), entry.getValue());
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


}
