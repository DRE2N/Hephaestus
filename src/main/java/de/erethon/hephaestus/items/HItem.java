package de.erethon.hephaestus.items;

import de.erethon.hephaestus.utils.HRandom;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class HItem {

    private final File file;
    private NamespacedKey key;
    private Item baseItem;
    private DataComponentPatch patch;

    private Map<Integer, Integer> levelWeights = new HashMap<>();
    private Map<Integer, Integer> slotWeights = new HashMap<>();
    private Map<String, Integer> rarityWeights = new HashMap<>();

    public HItem(File file) {
        this.file = file;
        load();
    }

    public HItem(NamespacedKey key, File file) {
        this.file = file;
    }

    public HItem(NamespacedKey key, Item baseItem, DataComponentPatch patch) {
        this.key = key;
        this.baseItem = baseItem;
        this.patch = patch;
        this.file = null;
    }

    public HItemStack rollRandomStack() {
        HItemStack hStack = new HItemStack(this);
        hStack.setItemLevel(HRandom.selectWeightedRandomValue(levelWeights));
        hStack.setRarity(HRarity.valueOf(HRandom.selectWeightedRandomValue(rarityWeights).toUpperCase(Locale.ROOT)));
        hStack.setMaxUpgrades(HRandom.selectWeightedRandomValue(slotWeights));
        return new HItemStack(this);
    }

    public NamespacedKey getKey() {
        return key;
    }

    public Item getBaseItem() {
        return baseItem;
    }

    public ItemStack update(ItemStack stack) {
        stack.applyComponents(patch);
        if (stack.getItem() != baseItem) {
            stack.setItem(baseItem); // Is there a better way?
        }
        CompoundTag compoundTag = new CompoundTag();
        compoundTag.putString("hephaestus-id", key.toString());
        CustomData.set(DataComponents.CUSTOM_DATA, stack, compoundTag);
        return stack;
    }

    public void update(org.bukkit.inventory.ItemStack stack) {
        update(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack));
    }

    private void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        key = NamespacedKey.fromString(config.getString("key", "hephaestus:default"));
        baseItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(config.getString("baseItem", "minecraft:stone")));
        String unhandled = config.getString("patch");
        if (unhandled != null) {
            ByteArrayInputStream buf = new ByteArrayInputStream(Base64.getDecoder().decode(unhandled));
            CompoundTag unhandledTag;
            try {
                unhandledTag = NbtIo.readCompressed(buf, NbtAccounter.unlimitedHeap());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            patch = DataComponentPatch.CODEC.parse(MinecraftServer.getDefaultRegistryAccess().createSerializationContext(NbtOps.INSTANCE), unhandledTag).result().get();
        }
        levelWeights = HRandom.loadWeights(config, "random.level");
        slotWeights =  HRandom.loadWeights(config, "random.slots");
        rarityWeights =  HRandom.loadWeights(config, "random.rarity");
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("key", key.toString());
        config.set("baseItem", BuiltInRegistries.ITEM.getKey(baseItem).toString());
        Tag tag = DataComponentPatch.CODEC.encodeStart(MinecraftServer.getDefaultRegistryAccess().createSerializationContext(NbtOps.INSTANCE), patch).getOrThrow();
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try {
            NbtIo.writeCompressed((CompoundTag) tag, buf);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.set("patch", Base64.getEncoder().encodeToString(buf.toByteArray()));
        try {
            config.save(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        config.createSection("random.level", levelWeights);
        config.createSection("random.slots", slotWeights);
        config.createSection("random.rarity", rarityWeights);
    }

}
