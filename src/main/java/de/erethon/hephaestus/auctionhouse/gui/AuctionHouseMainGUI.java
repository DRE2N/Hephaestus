package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Main auction house GUI hub - allows navigation to different sections.
 * Inspired by Guild Wars 2's Trading Post.
 */
public class AuctionHouseMainGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final boolean allowCollection;

    // Slot positions - 3 rows layout
    private static final int BROWSE_SLOT = 10;
    private static final int SELL_SLOT = 12;
    private static final int MY_ORDERS_SLOT = 14;
    private static final int COLLECTION_SLOT = 16;
    private static final int CLOSE_SLOT = 22;

    public AuctionHouseMainGUI(Hephaestus plugin, Player player, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 27,
            Component.text("Auction House", NamedTextColor.GOLD));

        setupInterface();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupInterface() {
        inventory.clear();

        // Browse items button
        ItemStack browseItem = createGuiItem(Material.COMPASS,
            "Browse & Buy",
            "Search and buy items",
            "from the trading post");
        inventory.setItem(BROWSE_SLOT, browseItem);

        // Sell items button
        ItemStack sellItem = createGuiItem(Material.GOLD_INGOT,
            "Sell Items",
            "List items from your",
            "inventory for sale");
        inventory.setItem(SELL_SLOT, sellItem);

        // My orders button
        ItemStack ordersItem = createGuiItem(Material.WRITABLE_BOOK,
            "My Orders",
            "View and manage your",
            "active buy/sell orders");
        inventory.setItem(MY_ORDERS_SLOT, ordersItem);

        // Collection button
        if (allowCollection) {
            ItemStack collectionItem = createGuiItem(Material.CHEST,
                "Collection",
                "Collect purchased items",
                "and earned money",
                "",
                NamedTextColor.GREEN + "Available");
            inventory.setItem(COLLECTION_SLOT, collectionItem);
        } else {
            ItemStack collectionItem = createGuiItem(Material.BARRIER,
                "Collection",
                "Visit a trading post to",
                "collect your items",
                "",
                NamedTextColor.RED + "Unavailable");
            inventory.setItem(COLLECTION_SLOT, collectionItem);
        }

        // Close button
        ItemStack closeItem = createGuiItem(Material.BARRIER, "Close");
        inventory.setItem(CLOSE_SLOT, closeItem);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, NamedTextColor.WHITE));

        if (lore.length > 0) {
            List<Component> loreComponents = new ArrayList<>();
            for (String line : lore) {
                loreComponents.add(Component.text(line, NamedTextColor.GRAY));
            }
            meta.lore(loreComponents);
        }

        item.setItemMeta(meta);
        return item;
    }

    @Override
    @org.jetbrains.annotations.NotNull
    public Inventory getInventory() {
        return inventory;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!event.getInventory().equals(inventory)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        event.setCancelled(true);

        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 27) return;

        switch (slot) {
            case BROWSE_SLOT -> {
                close();
                new BrowseItemsGUI(plugin, player, allowCollection).open();
            }
            case SELL_SLOT -> {
                close();
                new SellItemsGUI(plugin, player, allowCollection).open();
            }
            case MY_ORDERS_SLOT -> {
                close();
                new MyOrdersGUI(plugin, player, allowCollection).open();
            }
            case COLLECTION_SLOT -> {
                if (allowCollection) {
                    close();
                    new CollectionGUI(plugin, player).open();
                } else {
                    player.sendMessage(Component.text("You must visit a trading post to access collection!", NamedTextColor.RED));
                }
            }
            case CLOSE_SLOT -> close();
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            unregister();
        }
    }

    public void open() {
        player.openInventory(inventory);
    }

    private void close() {
        player.closeInventory();
        unregister();
    }

    private void unregister() {
        InventoryClickEvent.getHandlerList().unregister(this);
        InventoryCloseEvent.getHandlerList().unregister(this);
    }
}

