package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class HItemUpgrade {

    protected String id;
    private static final Map<String, Class<? extends HItemUpgrade>> UPGRADE_CLASSES = new HashMap<>();

    private final Set<NamespacedKey> validItems = new HashSet<>();
    private final Set<String> incompatibleUpgrades = new HashSet<>();
    private final Set<String> requiredUpgrades = new HashSet<>();
    private int minimumLevel = 0;

    static {
        UPGRADE_CLASSES.put("attribute_modifying", HAttributeModifyingUpgrade.class);
    }

    public static HItemUpgrade createInstance(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        String className = config.getString("type");
        Class<? extends HItemUpgrade> clazz = UPGRADE_CLASSES.get(className);
        if (clazz != null) {
            try {
                return clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    public HItemUpgrade() {
    }

    public HRolledUpgrade roll(HItemStack stack) {
        return null;
    }

    /**
     * Roll this upgrade as if at the provided level (may differ from the target item's level, e.g. orb level).
     * Default implementation delegates to roll(HItemStack) ignoring custom level.
     */
    public HRolledUpgrade rollAtLevel(HItemStack stack, int level) {
        return roll(stack);
    }

    public void update(HItemStack stack) {}

    public Set<String> getIncompatibleUpgrades() {
        return incompatibleUpgrades;
    }

    public Set<String> getRequiredUpgrades() {
        return requiredUpgrades;
    }

    public int getMinimumLevel() {
        return minimumLevel;
    }

    public Set<NamespacedKey> getValidItems() {
        return validItems;
    }

    public YamlConfiguration load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        id = config.getString("id");
        if (config.contains("name")) {
            ConfigurationSection nameSection = config.getConfigurationSection("name");
            if (nameSection == null) {
                return config;
            }
            for (String key : nameSection.getKeys(false)) {
                Locale locale;
                if (key.contains("de")) {
                    locale = Locale.GERMANY;
                } else {
                    locale = Locale.US;
                }
                Hephaestus.INSTANCE.registerTranslation("hephaestus.upgrade." + id + ".name", locale, config.getString("name." + key));
            }
        }
        if (config.contains("validItems")) {
            List<String> validItems = config.getStringList("validItems");
            for (String item : validItems) {
                this.validItems.add(NamespacedKey.fromString(item));
            }
        }
        if (config.contains("incompatibleUpgrades")) {
            incompatibleUpgrades.addAll(config.getStringList("incompatibleUpgrades"));
        }
        if (config.contains("requiredUpgrades")) {
            requiredUpgrades.addAll(config.getStringList("requiredUpgrades"));
        }
        minimumLevel = config.getInt("minimumLevel" ,0);
        return config;
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", id);
        config.set("type", UPGRADE_CLASSES.entrySet().stream().filter(e -> e.getValue().equals(this.getClass())).findFirst().get().getKey());
        try {
            config.save(file);
        } catch (Exception e) {
            Hephaestus.INSTANCE.getLogger().warning("Failed to save upgrade " + id);
            e.printStackTrace();
        }
    }

    public String getId() {
        return id;
    }
}
