package de.erethon.hephaestus;

import de.erethon.hephaestus.blocks.HBlockLibrary;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.listeners.HListener;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.kyori.adventure.translation.Translator;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.text.MessageFormat;
import java.util.Locale;

public final class Hephaestus extends JavaPlugin {

    public static Hephaestus INSTANCE;

    private final HItemLibrary itemLibrary;
    private final HBlockLibrary blockLibrary = new HBlockLibrary();
    GlobalTranslator globalTranslator = GlobalTranslator.translator();
    TranslationRegistry translationRegistry = TranslationRegistry.create(Key.key("hephaestus"));

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
        HListener itemListener = new HListener(this);
        Bukkit.getPluginManager().registerEvents(itemListener, this);
        Bukkit.getPluginManager().registerEvents(blockLibrary, this);
        itemLibrary.load();
        if (itemLibrary.get(BuiltInRegistries.ITEM.getKey(Items.DIAMOND)) == null) {
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

    public void registerTranslation(String key, Locale locale, String translation) {
        if (translationRegistry.contains(key))  {
            return;
        }
        translationRegistry.register(key, locale, new MessageFormat(translation));
    }


    private void generateDefaultItems() {
        getLogger().info("Generating default items... This may take a while.");
        int count = 0;
        for (Item item : BuiltInRegistries.ITEM.stream().toList()) {
            itemLibrary.register(new ItemStack(item), BuiltInRegistries.ITEM.getKey(item));
            getLogger().info("Registered " + BuiltInRegistries.ITEM.getKey(item));
            count++;
        }
        getLogger().info("Generated " + count + " default items.");
        itemLibrary.save();
    }
}
