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
import de.erethon.hephaestus.items.interactions.HItemEquipmentChangeAction;
import de.erethon.hephaestus.items.interactions.HItemInteractAction;
import de.erethon.hephaestus.utils.HRandom;
import net.minecraft.SharedConstants;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.PatchedDataComponentMap;
import net.minecraft.core.component.TypedDataComponent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.datafix.fixes.References;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

    // Randomization
    private Map<Integer, Integer> levelWeights = new HashMap<>();
    private Map<Integer, Map<String, Integer>> rarityWeights = new HashMap<>();
    private Map<Integer, Map<Integer, Integer>> slotWeights = new HashMap<>();

    // Interactions
    private final Set<HItemInteractAction> interactActions = new HashSet<>();
    private final Set<HItemEquipmentChangeAction> equipmentChangeActions = new HashSet<>();
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
        this.patch = patch;
        this.file = null;
    }

    public void registerInteractAction(HItemInteractAction action) {
        interactActions.add(action);
    }

    public void registerEquipmentChangeAction(HItemEquipmentChangeAction action) {
        equipmentChangeActions.add(action);
    }

    public void registerDropAction(HItemDropAction action) {
        dropActions.add(action);
    }

    public void runInteractActions(HItemStack stack, PlayerInteractEvent event) {
        interactActions.forEach(action -> action.onInteract(stack, event));
    }

    public void runEquipmentChangeActions(HItemStack stack, PlayerArmorChangeEvent event) {
        equipmentChangeActions.forEach(action -> action.onEquip(stack, event));
    }

    public void runDropActions(HItemStack stack, EntityDropItemEvent event) {
        dropActions.forEach(action -> action.onDrop(stack, event));
    }

    public HItemStack rollRandomStack() {
        HItemStack hStack = new HItemStack(this);
        hStack.update();

        int itemLevel = 0;
        if (levelWeights != null && !levelWeights.isEmpty()) {
            itemLevel = HRandom.selectWeightedRandomValue(levelWeights);
            hStack.setItemLevel(itemLevel);
        }

        Map<String, Integer> rarityWeightsForLevel = rarityWeights.getOrDefault(itemLevel, new HashMap<>());
        if (!rarityWeightsForLevel.isEmpty()) {
            hStack.setRarity(HRarity.valueOf(HRandom.selectWeightedRandomValue(rarityWeightsForLevel).toUpperCase(Locale.ROOT)));
        }

        Map<Integer, Integer> slotWeightsForLevel = slotWeights.getOrDefault(itemLevel, new HashMap<>());
        if (!slotWeightsForLevel.isEmpty()) {
            hStack.setMaxUpgrades(HRandom.selectWeightedRandomValue(slotWeightsForLevel));
        }

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

    public @NotNull Sound getPlacementSound() {
        if (placementSound == null) {
            return Sound.BLOCK_STONE_PLACE;
        }
        return CraftSound.minecraftToBukkit(placementSound);
    }

    public DataComponentPatch getPatch() {
        return patch;
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
            } catch (Exception e) {
                plugin.getLogger().warning("Error loading patch for item " + key + ": " + e.getMessage() + ", Data: " + config.getString("patch"));
                return;
            }
        }

        if (config.contains("placedBlockData")) {
            blockData = Bukkit.getServer().createBlockData(config.getString("placedBlockData"));
            plugin.getBlockLibrary().register(this, blockData);
        }
        if (config.contains("placementSound")) {
            placementSound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(config.getString("placementSound", "minecraft:block.stone.place"))).get().value();
        }
        levelWeights = HRandom.loadWeights(config, "random.level");
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
            // Add a custom data tag to the item, so we can identify it as a custom item if needed
            if (!(key.getNamespace().equals("minecraft"))) {
                CompoundTag tag = new CompoundTag();
                tag.putString("hephaestus-id", key.toString());
                DataComponentPatch.Builder customDataBuilder = DataComponentPatch.builder();
                customDataBuilder.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                customDataBuilder.copy(patch);
                patch = customDataBuilder.build();
            }

            DataComponentPatch defaultPatch = defaultBuilder.build();

            // This is used for reference, so we can copy things we want to change to our patch
            config.set("vanilla", serialize(defaultPatch));
            config.setComments("vanilla", List.of("JSON representation of the item's vanilla default data components.",
                    "This is never applied. Copy/paste values to the patch below to modify the item's properties."));

            // This contains our changes
            config.set("patch", serialize(patch));
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
        tag.addProperty("DataVersion", SharedConstants.getCurrentVersion().getDataVersion().getVersion());
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        return gson.toJson(tag);
    }

    public static DataComponentPatch deserialize(String string) {
        JsonObject element = JsonParser.parseString(string).getAsJsonObject();
        int dataVersion = element.get("DataVersion").getAsInt();
        int currentVersion = SharedConstants.getCurrentVersion().getDataVersion().getVersion();
        element = (JsonObject) MinecraftServer.getServer().fixerUpper.update(References.DATA_COMPONENTS, new Dynamic(JsonOps.INSTANCE, element), dataVersion, currentVersion).getValue();
        RegistryOps<JsonElement> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(JsonOps.INSTANCE);
        element.remove("DataVersion"); // This is not a component, so we can't deserialize it
        return DataComponentPatch.CODEC.decode(ops, element).getOrThrow().getFirst();
    }

}
