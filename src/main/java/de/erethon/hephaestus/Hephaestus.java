package de.erethon.hephaestus;

import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.listeners.HInventoryListener;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

public final class Hephaestus extends JavaPlugin {

    public static final NamespacedKey ITEM_KEY = new NamespacedKey("hephaestus", "id");
    public static final NamespacedKey ITEM_UPGRADES = new NamespacedKey("hephaestus", "upgrades");
    public static final Hephaestus INSTANCE = JavaPlugin.getPlugin(Hephaestus.class);

    private final HItemLibrary itemLibrary;
    private HInventoryListener inventoryListener;

    public Hephaestus(HItemLibrary itemLibrary) {
        super();
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void onEnable() {
        inventoryListener = new HInventoryListener(this);
        Bukkit.getPluginManager().registerEvents(inventoryListener, this);
        itemLibrary.load();
    }

    @Override
    public void onDisable() {
        itemLibrary.save();
    }

    public HItemLibrary getLibrary() {
        return itemLibrary;
    }
}
