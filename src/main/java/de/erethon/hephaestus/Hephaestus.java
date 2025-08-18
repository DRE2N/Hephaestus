package de.erethon.hephaestus;

import de.erethon.hephaestus.blocks.HBlockLibrary;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.listeners.HListener;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.TranslationRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.bukkit.Bukkit;
import org.bukkit.Material;
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
    private boolean translationSourceAdded = false; // ensure we only add once

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

    public static HItem getItem(NamespacedKey key) {
        return INSTANCE.getLibrary().get(key);
    }

    public static HItem getItem(String key) {
        return INSTANCE.getLibrary().get(key);
    }

    public static HItem registerNewFromBukkit(String key, Material material) {
        return registerNewFromBukkit(key, new org.bukkit.inventory.ItemStack(material));
    }

    public static HItem registerNewFromBukkit(String key, org.bukkit.inventory.ItemStack stack) {
        return INSTANCE.getLibrary().register(ItemStack.fromBukkitCopy(stack), ResourceLocation.parse(key));
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
        registerCommonTranslations();
        // Register our translation registry as a source (after initial registrations)
        if (!translationSourceAdded) {
            GlobalTranslator.translator().addSource(translationRegistry);
            translationSourceAdded = true;
            getLogger().info("Hephaestus translation source registered.");
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
        if (translation == null) {
            return;
        }
        // Allow multiple locales per key; only skip if this exact locale already has a value
        if (translationRegistry.translate(key, locale) != null) {
            return;
        }
        translationRegistry.register(key, locale, new MessageFormat(translation));
        // Optional lightweight debug (can be removed if noisy)
        // getLogger().fine("Registered translation: " + key + " (" + locale + ")");
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

    private void registerCommonTranslations() {
        // EN
        registerTranslation("hephaestus.rarity.trash.name", Locale.US, "Trash");
        registerTranslation("hephaestus.rarity.common.name", Locale.US, "Common");
        registerTranslation("hephaestus.rarity.uncommon.name", Locale.US, "Uncommon");
        registerTranslation("hephaestus.rarity.rare.name", Locale.US, "Rare");
        registerTranslation("hephaestus.rarity.epic.name", Locale.US, "Epic");
        registerTranslation("hephaestus.rarity.legendary.name", Locale.US, "Legendary");
        registerTranslation("hephaestus.rarity.mythic.name", Locale.US, "Mythical");
        // DE
        registerTranslation("hephaestus.rarity.trash.name", Locale.GERMANY, "Müll");
        registerTranslation("hephaestus.rarity.common.name", Locale.GERMANY, "Gewöhnlich");
        registerTranslation("hephaestus.rarity.uncommon.name", Locale.GERMANY, "Ungewöhnlich");
        registerTranslation("hephaestus.rarity.rare.name", Locale.GERMANY, "Selten");
        registerTranslation("hephaestus.rarity.epic.name", Locale.GERMANY, "Episch");
        registerTranslation("hephaestus.rarity.legendary.name", Locale.GERMANY, "Legendär");
        registerTranslation("hephaestus.rarity.mythic.name", Locale.GERMANY, "Mythisch");
    }
}
