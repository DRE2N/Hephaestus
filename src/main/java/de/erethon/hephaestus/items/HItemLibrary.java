package de.erethon.hephaestus.items;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.utils.HLibraryAction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class HItemLibrary {

    private final File itemDataDirectory;
    private final File upgradeDataDirectory;
    private final HashMap<NamespacedKey, HItem> items = new HashMap<>();
    private final HashMap<String, HItemUpgrade> upgrades = new HashMap<>();

    public HItemLibrary(File itemFile, File upgradeFile) {
        itemDataDirectory = itemFile;
        upgradeDataDirectory = upgradeFile;
    }

    // Items

    public HItem get(NamespacedKey key) {
        return items.get(key);
    }

    public HItemStack get(net.minecraft.world.item.ItemStack stack) {
        if (!stack.has(DataComponents.CUSTOM_DATA)) {
            HItem item = items.get(NamespacedKey.fromString(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
            if (item == null) {
                return null;
            }
            return new HItemStack(item, stack);
        }
        String id = stack.get(DataComponents.CUSTOM_DATA).getUnsafe().getString("hephaestus-id");
        HItem item = items.get(NamespacedKey.fromString(id));
        return new HItemStack(item, stack);
    }

    public HItemStack get(ItemStack stack) {
        return get(org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(stack));
    }

    public void runIfPresent(NamespacedKey key, HLibraryAction action) {
        HItem item = items.get(key);
        if (item != null) {
            action.execute(item);
        }
    }

    public boolean has(NamespacedKey key) {
        return items.containsKey(key);
    }

    public List<NamespacedKey> getKeys() {
        return new ArrayList<>(items.keySet());
    }

    // Upgrades

    public HItemUpgrade getUpgrade(String id) {
        return upgrades.get(id);
    }

    public void registerUpgrade(HItemUpgrade upgrade) {
        upgrades.put(upgrade.getId(), upgrade);
    }

    // Registration

    public void register(ItemStack item, NamespacedKey key){
        net.minecraft.world.item.ItemStack nmsItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        register(nmsItem, key);
    }

    public void register(net.minecraft.world.item.ItemStack item, NamespacedKey key) {
        HItem hItem = new HItem(key, item.getItem(), item.getComponentsPatch());
        items.put(key, hItem);
    }

    // Save/Load

    public void reload() {
        items.clear();
        load();
    }

    public void load() {
        loadFilesForDirectory(itemDataDirectory);
        loadFilesForDirectory(upgradeDataDirectory);
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
                HItemUpgrade upgrade = new HItemUpgrade();
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
            File file = new File(directory, item.getKey().getKey() + ".yml");
            item.save(file);
        }
    }
}
