package de.erethon.hephaestus.items;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.interactions.HItemDropAction;
import de.erethon.hephaestus.items.interactions.HItemEquipmentChangeAction;
import de.erethon.hephaestus.items.interactions.HItemInteractAction;
import de.erethon.hephaestus.utils.HRandom;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.SnbtPrinterTagVisitor;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
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
        baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.getString("baseItem", "minecraft:stone")));
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
            patch = deserialize(config.getString("patch"));
        }

        if (config.contains("placedBlockData")) {
            blockData = Bukkit.getServer().createBlockData(config.getString("placedBlockData"));
            plugin.getBlockLibrary().register(this, blockData);
        }
        if (config.contains("placementSound")) {
            placementSound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(config.getString("placementSound", "minecraft:block.stone.place")));
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
        config.set("baseItem", BuiltInRegistries.ITEM.getKey(baseItem).toString());
        try {
            if (patch == null) {
                patch = baseItem.getDefaultInstance().getComponentsPatch();
            }
            config.set("patch", serialize(patch));
        } catch (Exception e) {
            throw new RuntimeException(e);
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
        RegistryOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        final Tag tag = DataComponentPatch.CODEC.encodeStart(ops, patch).getOrThrow();
        return new SnbtPrinterTagVisitor().visit(tag);
   }

    public static DataComponentPatch deserialize(String string) {
        RegistryOps<Tag> ops = CraftRegistry.getMinecraftRegistry().createSerializationContext(NbtOps.INSTANCE);
        TagParser parser = new TagParser(new StringReader(string));
        DataComponentPatch patch = null;
        try {
            patch = DataComponentPatch.CODEC.parse(ops, parser.readValue()).getOrThrow();
        } catch (CommandSyntaxException e) {
            throw new RuntimeException(e);
        }
        return patch;
    }

}
