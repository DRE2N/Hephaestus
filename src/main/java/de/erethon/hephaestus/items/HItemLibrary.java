package de.erethon.hephaestus.items;

import de.erethon.hephaestus.utils.HLibraryAction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.HashMap;

public class HItemLibrary {

    private final File dataDirectory;
    private final HashMap<NamespacedKey, HItem> items = new HashMap<>();

    public HItemLibrary(File file) {
        dataDirectory = file;
    }

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

    public void reload() {
        items.clear();
        load();
    }

    public void load() {
        loadFilesForDirectory(dataDirectory);
    }

    public void register(ItemStack item, NamespacedKey key){
        net.minecraft.world.item.ItemStack nmsItem = org.bukkit.craftbukkit.inventory.CraftItemStack.asNMSCopy(item);
        register(nmsItem, key);
    }

    public void register(net.minecraft.world.item.ItemStack item, NamespacedKey key) {
        HItem hItem = new HItem(key, item.getItem(), item.getComponentsPatch());
        items.put(key, hItem);
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
