package de.erethon.hephaestus;

import de.erethon.hephaestus.items.HBlockLibrary;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.listeners.HListener;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import javax.annotation.Nullable;
import java.util.Locale;

public final class Hephaestus extends JavaPlugin {

    public static Hephaestus INSTANCE;

    private final HItemLibrary itemLibrary;
    private final HBlockLibrary blockLibrary = new HBlockLibrary();
    private HListener inventoryListener;
    private Translator translator;

    public Hephaestus(HItemLibrary itemLibrary) {
        super();
        this.itemLibrary = itemLibrary;
        INSTANCE = this;
    }

    // Utility methods for quick access to the item library
    public static HItemStack getStack(ItemStack stack) {
        return INSTANCE.getLibrary().get(stack);
    }

    public static HItemStack getStack(org.bukkit.inventory.ItemStack stack) {
        return INSTANCE.getLibrary().get(stack);
    }

    @Override
    public void onEnable() {
        inventoryListener = new HListener(this);
        Bukkit.getPluginManager().registerEvents(inventoryListener, this);
        Bukkit.getPluginManager().registerEvents(blockLibrary, this);
        itemLibrary.load();
        if (itemLibrary.get(NamespacedKey.fromString("minecraft:diamond")) == null) {
            getLogger().warning("No vanilla items found. Generating default items...");
            generateDefaultItems();
        }
    }

    @Override
    public void onDisable() {
        itemLibrary.save();
    }

    public HItemLibrary getLibrary() {
        return itemLibrary;
    }

    public HBlockLibrary getBlockLibrary() {
        return blockLibrary;
    }


    private void generateDefaultItems() {
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            NamespacedKey key = NamespacedKey.fromString(BuiltInRegistries.ITEM.getKey(item).toString());
            itemLibrary.register(new ItemStack(item), key);
            getLogger().info("Registered " + key.toString());
            count++;
        }
        getLogger().info("Generated " + count + " default items.");
        itemLibrary.save();
    }
}
