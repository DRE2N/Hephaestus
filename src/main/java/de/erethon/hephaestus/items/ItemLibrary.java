package de.erethon.hephaestus.items;

import org.bukkit.NamespacedKey;

import java.io.File;
import java.util.HashMap;

public class ItemLibrary {

    private final File dataDirectory;
    private final HashMap<NamespacedKey, HItem> items = new HashMap<>();

    public ItemLibrary(File file) {
        dataDirectory = file;
        load();
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

    private void load() {
        loadFilesForDirectory(dataDirectory);
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
}
