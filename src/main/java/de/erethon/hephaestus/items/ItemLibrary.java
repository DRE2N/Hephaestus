package de.erethon.hephaestus.items;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;

public class ItemLibrary {

    private final File dataDirectory;
    private final HashMap<NamespacedKey, HItem> items = new HashMap<>();

    public ItemLibrary(File file) {
        dataDirectory = file;
    }

    public HItem get(NamespacedKey key) {
        return items.get(key);
    }

    public boolean has(NamespacedKey key) {
        return items.containsKey(key);
    }

    public void reload() {
        items.clear();
        load();
    }

    public void load() {
        loadFilesForDirectory(dataDirectory);
    }

    public void register(ItemStack item, NamespacedKey key){
        net.minecraft.world.item.ItemStack nmsItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        HItem hItem = new HItem(key, nmsItem.getItem(), nmsItem.getComponentsPatch());
        items.put(key, hItem);
        saveFilesForDirectory(dataDirectory);
    }

    public void save() {
        saveFilesForDirectory(dataDirectory);
    }

    private void loadFilesForDirectory(File directory) {
        if (directory == null || directory.listFiles() == null) {
            return;
        }
        for (File file : directory.listFiles()) {
            if (file.isDirectory()) {
                loadFilesForDirectory(file);
                continue;
            }
            if (file.getName().endsWith(".yml")) {
                HItem item = new HItem(file);
                items.put(item.getKey(), item);
            }
        }
    }

    private void saveFilesForDirectory(File directory) {
        if (directory == null) {
            directory.mkdirs();
        }
        for (HItem item : items.values()) {
            File file = new File(directory, item.getKey().getKey() + ".yml");
            item.save(file);
        }
    }
}
