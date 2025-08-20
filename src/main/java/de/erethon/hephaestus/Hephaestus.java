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

    private void registerCommonTranslations() {
        // Rarities
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

        // HUpgradeResults
        registerTranslation("hephaestus.upgrade.result.success", Locale.US, "Upgrade successful!");
        registerTranslation("hephaestus.upgrade.result.success", Locale.GERMANY, "Upgrade erfolgreich!");
        registerTranslation("hephaestus.upgrade.too_many_upgrades", Locale.US, "This item already has the maximum number of upgrades.");
        registerTranslation("hephaestus.upgrade.too_many_upgrades", Locale.GERMANY, "Dieses Item hat bereits die maximale Anzahl an Upgrades.");
        registerTranslation("hephaestus.upgrade.incompatible_upgrade", Locale.US, "This upgrade is incompatible with the current item.");
        registerTranslation("hephaestus.upgrade.incompatible_upgrade", Locale.GERMANY, "Dieses Upgrade ist inkompatibel mit dem aktuellen Item.");
        registerTranslation("hephaestus.upgrade.too_bad_rarity", Locale.US, "The item's rarity is too low for this upgrade.");
        registerTranslation("hephaestus.upgrade.too_bad_rarity", Locale.GERMANY, "Die Seltenheit des Items ist zu niedrig für dieses Upgrade.");
        registerTranslation("hephaestus.upgrade.missing_required_upgrade", Locale.US, "This upgrade requires another upgrade that is not present.");
        registerTranslation("hephaestus.upgrade.missing_required_upgrade", Locale.GERMANY, "Dieses Upgrade benötigt ein anderes Upgrade, das nicht vorhanden ist.");
        registerTranslation("hephaestus.upgrade.invalid_item", Locale.US, "The item is not valid for this upgrade.");
        registerTranslation("hephaestus.upgrade.invalid_item", Locale.GERMANY, "Das Item ist für dieses Upgrade nicht gültig.");
        registerTranslation("hephaestus.upgrade.too_low_level", Locale.US, "The item's level is too low for this upgrade.");
        registerTranslation("hephaestus.upgrade.too_low_level", Locale.GERMANY, "Das Item-Level ist zu niedrig für dieses Upgrade.");
        registerTranslation("hephaestus.upgrade.no_empty_socket", Locale.US, "There are no empty sockets available for this upgrade.");
        registerTranslation("hephaestus.upgrade.no_empty_socket", Locale.GERMANY, "Es sind keine leeren Sockel für dieses Upgrade verfügbar.");
        registerTranslation("hephaestus.upgrade.socket_color_mismatch", Locale.US, "The socket color does not match the upgrade's color.");
        registerTranslation("hephaestus.upgrade.socket_color_mismatch", Locale.GERMANY, "Die Sockelfarbe stimmt nicht mit der Farbe des Upgrades überein.");
        registerTranslation("hephaestus.upgrade.invalid_upgrade", Locale.US, "The upgrade is not valid for this item.");
        registerTranslation("hephaestus.upgrade.invalid_upgrade", Locale.GERMANY, "Das Upgrade ist für dieses Item nicht gültig.");

        // Upgrade lore
        registerTranslation("hephaestus.upgrade.color", Locale.US, "Color: ");
        registerTranslation("hephaestus.upgrade.color", Locale.GERMANY, "Farbe: ");
        registerTranslation("hephaestus.upgrade.grants", Locale.US, "Grants: ");
        registerTranslation("hephaestus.upgrade.grants", Locale.GERMANY, "Gewährt: ");
        registerTranslation("hephaestus.upgrade.empty", Locale.US, "<empty>");
        registerTranslation("hephaestus.upgrade.empty", Locale.GERMANY, "<leer>");

        // Attributes - Let's only do the common ones for now
        // advantage_physical
        registerTranslation("hephaestus.attribute.advantage_physical.name", Locale.US, "Physical Damage");
        registerTranslation("hephaestus.attribute.advantage_physical.name", Locale.GERMANY, "Physischer Schaden");
        // advantage_magical
        registerTranslation("hephaestus.attribute.advantage_magical.name", Locale.US, "Magical Damage");
        registerTranslation("hephaestus.attribute.advantage_magical.name", Locale.GERMANY, "Magischer Schaden");
        // advantage_fire
        registerTranslation("hephaestus.attribute.advantage_fire.name", Locale.US, "Fire Damage");
        registerTranslation("hephaestus.attribute.advantage_fire.name", Locale.GERMANY, "Feuerschaden");
        // advantage_water
        registerTranslation("hephaestus.attribute.advantage_water.name", Locale.US, "Water Damage");
        registerTranslation("hephaestus.attribute.advantage_water.name", Locale.GERMANY, "Wasserschaden");
        // advantage_earth
        registerTranslation("hephaestus.attribute.advantage_earth.name", Locale.US, "Earth Damage");
        registerTranslation("hephaestus.attribute.advantage_earth.name", Locale.GERMANY, "Erdschaden");
        // advantage_air
        registerTranslation("hephaestus.attribute.advantage_air.name", Locale.US, "Air Damage");
        registerTranslation("hephaestus.attribute.advantage_air.name", Locale.GERMANY, "Luftschaden");
        // resistance_physical
        registerTranslation("hephaestus.attribute.resistance_physical.name", Locale.US, "Physical Resistance");
        registerTranslation("hephaestus.attribute.resistance_physical.name", Locale.GERMANY, "Physische Resistenz");
        // resistance_magical
        registerTranslation("hephaestus.attribute.resistance_magical.name", Locale.US, "Magical Resistance");
        registerTranslation("hephaestus.attribute.resistance_magical.name", Locale.GERMANY, "Magische Resistenz");
        // resistance_fire
        registerTranslation("hephaestus.attribute.resistance_fire.name", Locale.US, "Fire Resistance");
        registerTranslation("hephaestus.attribute.resistance_fire.name", Locale.GERMANY, "Feuerresistenz");
        // resistance_water
        registerTranslation("hephaestus.attribute.resistance_water.name", Locale.US, "Water Resistance");
        registerTranslation("hephaestus.attribute.resistance_water.name", Locale.GERMANY, "Wasserresistenz");
        // resistance_earth
        registerTranslation("hephaestus.attribute.resistance_earth.name", Locale.US, "Earth Resistance");
        registerTranslation("hephaestus.attribute.resistance_earth.name", Locale.GERMANY, "Erdresistenz");
        // resistance_air
        registerTranslation("hephaestus.attribute.resistance_air.name", Locale.US, "Air Resistance");
        registerTranslation("hephaestus.attribute.resistance_air.name", Locale.GERMANY, "Luftresistenz");
        // penetration_physical
        registerTranslation("hephaestus.attribute.penetration_physical.name", Locale.US, "Physical Penetration");
        registerTranslation("hephaestus.attribute.penetration_physical.name", Locale.GERMANY, "Physische Durchdringung");
        // penetration_magical
        registerTranslation("hephaestus.attribute.penetration_magical.name", Locale.US, "Magical Penetration");
        registerTranslation("hephaestus.attribute.penetration_magical.name", Locale.GERMANY, "Magische Durchdringung");
        // penetration_fire
        registerTranslation("hephaestus.attribute.penetration_fire.name", Locale.US, "Fire Penetration");
        registerTranslation("hephaestus.attribute.penetration_fire.name", Locale.GERMANY, "Feuerdurchdringung");
        // penetration_water
        registerTranslation("hephaestus.attribute.penetration_water.name", Locale.US, "Water Penetration");
        registerTranslation("hephaestus.attribute.penetration_water.name", Locale.GERMANY, "Wasserdurchdringung");
        // penetration_earth
        registerTranslation("hephaestus.attribute.penetration_earth.name", Locale.US, "Earth Penetration");
        registerTranslation("hephaestus.attribute.penetration_earth.name", Locale.GERMANY, "Erddurchdringung");
        // penetration_air
        registerTranslation("hephaestus.attribute.penetration_air.name", Locale.US, "Air Penetration");
        registerTranslation("hephaestus.attribute.penetration_air.name", Locale.GERMANY, "Luftdurchdringung");
        // stat_crit_damage
        registerTranslation("hephaestus.attribute.stat_crit_damage.name", Locale.US, "Critical Damage");
        registerTranslation("hephaestus.attribute.stat_crit_damage.name", Locale.GERMANY, "Kritischer Schaden");
        // stat_crit_chance
        registerTranslation("hephaestus.attribute.stat_crit_chance.name", Locale.US, "Critical Chance");
        registerTranslation("hephaestus.attribute.stat_crit_chance.name", Locale.GERMANY, "Kritische Chance");
        // attack_speed
        registerTranslation("hephaestus.attribute.attack_speed.name", Locale.US, "Attack Speed");
        registerTranslation("hephaestus.attribute.attack_speed.name", Locale.GERMANY, "Angriffsgeschwindigkeit");
        // stat_healingpower
        registerTranslation("hephaestus.attribute.stat_healingpower.name", Locale.US, "Healing Power");
        registerTranslation("hephaestus.attribute.stat_healingpower.name", Locale.GERMANY, "Heilungskraft");
        // stat_energy_regen
        registerTranslation("hephaestus.attribute.stat_energy_regen.name", Locale.US, "Energy Regeneration");
        registerTranslation("hephaestus.attribute.stat_energy_regen.name", Locale.GERMANY, "Energie-Regeneration");
        // stat_health_regen
        registerTranslation("hephaestus.attribute.stat_health_regen.name", Locale.US, "Health Regeneration");
        registerTranslation("hephaestus.attribute.stat_health_regen.name", Locale.GERMANY, "Lebens-Regeneration");
        // stat_cdr
        registerTranslation("hephaestus.attribute.stat_cdr.name", Locale.US, "Cooldown Reduction");
        registerTranslation("hephaestus.attribute.stat_cdr.name", Locale.GERMANY, "Abklingzeitverringerung");
        // stat_tenacity
        registerTranslation("hephaestus.attribute.stat_tenacity.name", Locale.US, "Tenacity");
        registerTranslation("hephaestus.attribute.stat_tenacity.name", Locale.GERMANY, "Zähigkeit");
        // max_health
        registerTranslation("hephaestus.attribute.max_health.name", Locale.US, "Maximum Health");
        registerTranslation("hephaestus.attribute.max_health.name", Locale.GERMANY, "Maximales Leben");
        // movement_speed
        registerTranslation("hephaestus.attribute.movement_speed.name", Locale.US, "Movement Speed");
        registerTranslation("hephaestus.attribute.movement_speed.name", Locale.GERMANY, "Bewegungsgeschwindigkeit");
        // safe_fall_distance
        registerTranslation("hephaestus.attribute.safe_fall_distance.name", Locale.US, "Safe Fall Distance");
        registerTranslation("hephaestus.attribute.safe_fall_distance.name", Locale.GERMANY, "Sichere Fallhöhe");
    }
}
