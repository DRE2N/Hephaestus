package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.BuyOrder;
import de.erethon.hephaestus.auctionhouse.SellOrder;
import de.erethon.hephaestus.items.HItem;
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
 * GUI for viewing buy and sell orders for a specific item.
 * Shows best prices and allows instant buy/sell or creating orders.
 */
public class ItemOrdersGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final HItem item;
    private final boolean allowCollection;

    private List<SellOrder> sellOrders = new ArrayList<>();
    private List<BuyOrder> buyOrders = new ArrayList<>();

    private static final int ITEM_DISPLAY_SLOT = 4;
    private static final int INSTANT_BUY_SLOT = 20;
    private static final int CREATE_BUY_ORDER_SLOT = 29;
    private static final int INSTANT_SELL_SLOT = 24;
    private static final int CREATE_SELL_ORDER_SLOT = 33;
    private static final int BACK_SLOT = 49;

    // Sell order display slots (left side)
    private static final int[] SELL_ORDER_SLOTS = {10, 11, 12, 19, 21, 28, 29, 30};
    // Buy order display slots (right side)
    private static final int[] BUY_ORDER_SLOTS = {14, 15, 16, 23, 25, 32, 33, 34};

    public ItemOrdersGUI(Hephaestus plugin, Player player, HItem item, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.item = item;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Orders: " + item.getKey().toString(), NamedTextColor.GOLD));

        loadOrders();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadOrders() {
        String itemId = item.getKey().toString();

        auctionHouse.getSellOrders(itemId, "").thenAccept(orders -> {
            this.sellOrders = orders;
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });

        auctionHouse.getBuyOrders(itemId, "").thenAccept(orders -> {
            this.buyOrders = orders;
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });
    }

    private void setupInterface() {
        inventory.clear();

        // Background
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 54; i++) {
            inventory.setItem(i, background);
        }

        // Item display
        ItemStack itemDisplay = item.createStack().getBukkitStack().clone();
        ItemMeta meta = itemDisplay.getItemMeta();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        lore.add(Component.text("Sell Orders: " + sellOrders.size(), NamedTextColor.YELLOW));
        lore.add(Component.text("Buy Orders: " + buyOrders.size(), NamedTextColor.YELLOW));
        if (meta.hasLore() && meta.lore() != null) {
            lore.add(Component.empty());
            lore.addAll(meta.lore());
        }
        meta.lore(lore);
        itemDisplay.setItemMeta(meta);
        inventory.setItem(ITEM_DISPLAY_SLOT, itemDisplay);

        // Display sell orders (left side - green)
        for (int i = 0; i < Math.min(sellOrders.size(), SELL_ORDER_SLOTS.length); i++) {
            SellOrder order = sellOrders.get(i);
            ItemStack orderItem = createSellOrderDisplay(order, i == 0);
            inventory.setItem(SELL_ORDER_SLOTS[i], orderItem);
        }

        // Display buy orders (right side - red)
        for (int i = 0; i < Math.min(buyOrders.size(), BUY_ORDER_SLOTS.length); i++) {
            BuyOrder order = buyOrders.get(i);
            ItemStack orderItem = createBuyOrderDisplay(order, i == 0);
            inventory.setItem(BUY_ORDER_SLOTS[i], orderItem);
        }

        // Instant buy button
        if (!sellOrders.isEmpty()) {
            SellOrder bestSell = sellOrders.get(0);
            ItemStack instantBuy = createGuiItem(Material.EMERALD,
                "Instant Buy",
                "Price: " + bestSell.pricePerUnit() + " per unit",
                "Available: " + bestSell.quantity(),
                "",
                NamedTextColor.GREEN + "Click to purchase");
            inventory.setItem(INSTANT_BUY_SLOT, instantBuy);
        } else {
            ItemStack noBuy = createGuiItem(Material.BARRIER,
                "No Sell Orders",
                "Create a buy order instead");
            inventory.setItem(INSTANT_BUY_SLOT, noBuy);
        }

        // Create buy order button
        ItemStack createBuy = createGuiItem(Material.WRITABLE_BOOK,
            "Create Buy Order",
            "Set your own price",
            "and wait for sellers");
        inventory.setItem(CREATE_BUY_ORDER_SLOT, createBuy);

        // Instant sell button (only if in collection mode)
        if (allowCollection && !buyOrders.isEmpty()) {
            BuyOrder bestBuy = buyOrders.get(0);
            ItemStack instantSell = createGuiItem(Material.GOLD_INGOT,
                "Instant Sell",
                "Price: " + bestBuy.pricePerUnit() + " per unit",
                "Demand: " + bestBuy.quantity(),
                "",
                NamedTextColor.GREEN + "Click to sell from inventory");
            inventory.setItem(INSTANT_SELL_SLOT, instantSell);
        } else if (!allowCollection) {
            ItemStack noSell = createGuiItem(Material.BARRIER,
                "Instant Sell Unavailable",
                "Visit a trading post to sell");
            inventory.setItem(INSTANT_SELL_SLOT, noSell);
        } else {
            ItemStack noSell = createGuiItem(Material.BARRIER,
                "No Buy Orders",
                "Create a sell order instead");
            inventory.setItem(INSTANT_SELL_SLOT, noSell);
        }

        // Create sell order button
        ItemStack createSell = createGuiItem(Material.GOLD_INGOT,
            "Create Sell Order",
            "List your items for sale",
            "at your desired price");
        inventory.setItem(CREATE_SELL_ORDER_SLOT, createSell);

        // Back button
        ItemStack back = createGuiItem(Material.ARROW, "Back to Browse");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack createSellOrderDisplay(SellOrder order, boolean isBest) {
        Material mat = isBest ? Material.LIME_DYE : Material.GREEN_DYE;
        String prefix = isBest ? "BEST: " : "";

        return createGuiItem(mat,
            prefix + "Sell Order",
            "Price: " + order.pricePerUnit() + " per unit",
            "Quantity: " + order.quantity(),
            "Total: " + order.getTotalPrice());
    }

    private ItemStack createBuyOrderDisplay(BuyOrder order, boolean isBest) {
        Material mat = isBest ? Material.ORANGE_DYE : Material.RED_DYE;
        String prefix = isBest ? "BEST: " : "";

        return createGuiItem(mat,
            prefix + "Buy Order",
            "Price: " + order.pricePerUnit() + " per unit",
            "Quantity: " + order.quantity(),
            "Total: " + order.getTotalPrice());
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
        if (slot < 0 || slot >= 54) return;

        if (slot == INSTANT_BUY_SLOT && !sellOrders.isEmpty()) {
            close();
            new InstantBuyGUI(plugin, player, item, sellOrders.get(0), allowCollection).open();
        } else if (slot == CREATE_BUY_ORDER_SLOT) {
            player.sendMessage(Component.text("Use chat to create buy order: /ah buy " + item.getKey() + " <quantity> <price>", NamedTextColor.YELLOW));
        } else if (slot == INSTANT_SELL_SLOT && allowCollection && !buyOrders.isEmpty()) {
            player.sendMessage(Component.text("Place the item in your hand and use: /ah sell <price>", NamedTextColor.YELLOW));
        } else if (slot == CREATE_SELL_ORDER_SLOT) {
            close();
            new AuctionHouseMainGUI(plugin, player, allowCollection).open();
            player.sendMessage(Component.text("Navigate to 'Sell Items' to create sell orders", NamedTextColor.YELLOW));
        } else if (slot == BACK_SLOT) {
            close();
            new BrowseItemsGUI(plugin, player, allowCollection).open();
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

