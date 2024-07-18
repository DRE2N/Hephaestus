package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.utils.HLoreEntry;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class HItemUpgrade implements HLoreEntry {

    protected String id;
    private final Set<NamespacedKey> validItems = new HashSet<>();
    private final Set<String> incompatibleUpgrades = new HashSet<>();
    private final Set<String> requiredUpgrades = new HashSet<>();
    private final int minimumLevel = 0;

    public HItemUpgrade() {
    }

    public HRolledUpgrade roll(HItemStack stack) {
        return null;
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
        return config;
    }

    public void save(File file) {
        YamlConfiguration config = new YamlConfiguration();
        config.set("id", id);
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

    public List<Component> getLore() {
        return null;
    }
}
