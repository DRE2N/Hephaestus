package de.erethon.hephaestus;

import de.erethon.hephaestus.items.ItemLibrary;
import de.erethon.hephaestus.listeners.InventoryListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class Hephaestus extends JavaPlugin {

    private final ItemLibrary itemLibrary;
    private InventoryListener inventoryListener;

    public Hephaestus(ItemLibrary itemLibrary) {
        super();
        this.itemLibrary = itemLibrary;
    }

    @Override
    public void onEnable() {
        inventoryListener = new InventoryListener(this);
        Bukkit.getPluginManager().registerEvents(inventoryListener, this);
        itemLibrary.load();
    }

    @Override
    public void onDisable() {
        itemLibrary.save();
    }

    public ItemLibrary getLibrary() {
        return itemLibrary;
    }
}
