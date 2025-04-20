package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.utils.HLibraryAction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class HItemLibrary {

    private final File itemDataDirectory;
    private final File upgradeDataDirectory;
    private final HashMap<ResourceLocation, HItem> items = new HashMap<>();
    private final HashMap<String, HItemUpgrade> upgrades = new HashMap<>();

    public HItemLibrary(File itemFile, File upgradeFile) {
        itemDataDirectory = itemFile;
        upgradeDataDirectory = upgradeFile;
    }

    // Items

    public HItem get(NamespacedKey key) {
        return items.get(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));
    }

    public HItem get(ResourceLocation key) {
        return items.get(key);
    }

    public HItem get(String key) {
        return items.get(ResourceLocation.parse(key));
    }

    public HItemStack get(net.minecraft.world.item.ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            HItem item = items.get(BuiltInRegistries.ITEM.getKey(stack.getItem()));
            if (item == null) {
                return null;
            }
            return new HItemStack(item, stack);
        }
        Optional<String> id = stack.get(DataComponents.CUSTOM_DATA).getUnsafe().getString("hephaestus-id");
        if (id.isEmpty()) {
            return null;
        }
        HItem item = items.get(ResourceLocation.parse(id.get()));
        return new HItemStack(item, stack);
    }

    public HItemStack get(ItemStack stack) {
        return get(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack));
    }

    public void runIfPresent(NamespacedKey key, HLibraryAction action) {
        ResourceLocation loc = ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey());
        HItem item = items.get(loc);
        if (item != null) {
            action.execute(item);
        }
    }

    public void runIfPresent(ResourceLocation key, HLibraryAction action) {
        HItem item = items.get(key);
        if (item != null) {
            action.execute(item);
        }
    }

    public boolean has(NamespacedKey key) {
        return items.containsKey(ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));
    }

    public boolean has(ResourceLocation key) {
        return items.containsKey(key);
    }

    public List<ResourceLocation> getKeys() {
        return new ArrayList<>(items.keySet());
    }

    // Upgrades

    public HItemUpgrade getUpgrade(String id) {
        return upgrades.get(id);
    }

    public void registerUpgrade(HItemUpgrade upgrade) {
        upgrades.put(upgrade.getId(), upgrade);
    }

    public List<String> getUpgradeKeys() {
        return new ArrayList<>(upgrades.keySet());
    }

    // Registration

    public HItem register(ItemStack item, NamespacedKey key){
        net.minecraft.world.item.ItemStack nmsItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        return register(nmsItem, ResourceLocation.fromNamespaceAndPath(key.getNamespace(), key.getKey()));
    }

    public HItem register(net.minecraft.world.item.ItemStack item, ResourceLocation key) {
        HItem hItem = new HItem(key, item.getItem(), item.getComponentsPatch());
        items.put(key, hItem);
        return hItem;
    }

    // Save/Load

    public void reload() {
        items.clear();
        upgrades.clear();
        load();
    }

    public void load() {
        loadFilesForDirectory(itemDataDirectory);
        Hephaestus.INSTANCE.getLogger().info("Loaded " + items.size() + " items.");
        loadFilesForDirectory(upgradeDataDirectory);
        Hephaestus.INSTANCE.getLogger().info("Loaded " + upgrades.size() + " upgrades.");
    }

    public void save() {
        saveFilesForDirectory(itemDataDirectory);
        saveFilesForDirectory(upgradeDataDirectory);
    }

    private void loadFilesForDirectory(File directory) {
        if (directory == null || directory.listFiles() == null) {
            return;
        }
        boolean isUpgradeDirectory = directory.equals(upgradeDataDirectory);
        Hephaestus.INSTANCE.getLogger().info("Loading " + directory.getName());
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                loadFilesForDirectory(file);
                continue;
            }
            if (file.getName().endsWith(".yml") && !isUpgradeDirectory) {
                try {
                    HItem item = new HItem(file);
                    items.put(item.getKey(), item);
                    continue;
                } catch (Exception e) {
                    Hephaestus.INSTANCE.getLogger().warning("Failed to load item " + file.getName());
                    e.printStackTrace();
                    continue;
                }
            }
            if (file.getName().endsWith(".yml") && isUpgradeDirectory) {
                HItemUpgrade upgrade = HItemUpgrade.createInstance(file);
                if (upgrade == null) {
                    Hephaestus.INSTANCE.getLogger().warning("Failed to load upgrade " + file.getName());
                    continue;
                }
                try {
                    upgrade.load(file);
                    upgrades.put(upgrade.getId(), upgrade);
                } catch (Exception e) {
                    Hephaestus.INSTANCE.getLogger().warning("Failed to load upgrade " + file.getName());
                    e.printStackTrace();
                }
            }
        }
    }

    private void saveFilesForDirectory(File directory) {
        if (directory == null) {
            directory.mkdirs();
        }
        boolean isUpgradeDirectory = directory.equals(upgradeDataDirectory);
        if (isUpgradeDirectory) {
            for (HItemUpgrade upgrade : upgrades.values()) {
                File file = new File(directory, upgrade.getId() + ".yml");
                upgrade.save(file);
            }
            return;
        }
        for (HItem item : items.values()) {
            File subDirectory = new File(directory, item.getKey().getNamespace());
            File file = new File(subDirectory, item.getKey().getPath() + ".yml");
            item.save(file);
        }
    }
}
