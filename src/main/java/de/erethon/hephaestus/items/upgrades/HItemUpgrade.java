package de.erethon.hephaestus.items.upgrades;

import de.erethon.hephaestus.items.HItemStack;
import net.minecraft.nbt.CompoundTag;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

public class HItemUpgrade {

    protected String id;
    private final Set<NamespacedKey> validItems = new HashSet<>();
    private final Set<String> incompatibleUpgrades = new HashSet<>();
    private final Set<String> requiredUpgrades = new HashSet<>();
    private final int minimumLevel = 0;

    public HItemUpgrade() {
    }

    public void roll(HItemStack stack) {}

    public void update(HItemStack stack) {}

    public YamlConfiguration load(File file) {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
        id = config.getString("id");
        return config;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", id);
        return tag;
    }
}
