package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.BuyOrder;
import de.erethon.hephaestus.auctionhouse.SellOrder;
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
 * GUI for viewing and managing player's active buy and sell orders.
 */
public class MyOrdersGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final boolean allowCollection;

    private List<SellOrder> sellOrders = new ArrayList<>();
    private List<BuyOrder> buyOrders = new ArrayList<>();
    private int currentPage = 0;
    private boolean viewingSellOrders = true; // true = sell orders, false = buy orders

    private static final int ORDERS_PER_PAGE = 21; // 3 rows of 7

    private static final int TOGGLE_VIEW_SLOT = 4;
    private static final int PREV_PAGE_SLOT = 45;
    private static final int NEXT_PAGE_SLOT = 53;
    private static final int BACK_SLOT = 49;

    public MyOrdersGUI(Hephaestus plugin, Player player, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("My Orders", NamedTextColor.GOLD));

        loadOrders();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadOrders() {
        auctionHouse.getPlayerSellOrders(player.getUniqueId()).thenAccept(orders -> {
            this.sellOrders = orders;
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });

        auctionHouse.getPlayerBuyOrders(player.getUniqueId()).thenAccept(orders -> {
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

        // Toggle view button
        String viewType = viewingSellOrders ? "Sell Orders" : "Buy Orders";
        String otherType = viewingSellOrders ? "Buy Orders" : "Sell Orders";
        ItemStack toggleView = createGuiItem(
            viewingSellOrders ? Material.EMERALD : Material.GOLD_INGOT,
            "Viewing: " + viewType,
            "Click to view " + otherType,
            "",
            "Sell Orders: " + sellOrders.size(),
            "Buy Orders: " + buyOrders.size());
        inventory.setItem(TOGGLE_VIEW_SLOT, toggleView);

        // Display orders
        List<?> currentOrders = viewingSellOrders ? sellOrders : buyOrders;
        int startIndex = currentPage * ORDERS_PER_PAGE;
        int endIndex = Math.min(startIndex + ORDERS_PER_PAGE, currentOrders.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            ItemStack orderItem;
            if (viewingSellOrders) {
                orderItem = createSellOrderItem((SellOrder) currentOrders.get(i));
            } else {
                orderItem = createBuyOrderItem((BuyOrder) currentOrders.get(i));
            }
            inventory.setItem(slot, orderItem);

            slot++;
            // Skip rightmost columns
            if ((slot + 1) % 9 == 0) {
                slot += 2;
            }
            // Stop at row 4
            if (slot >= 37) {
                break;
            }
        }

        // Navigation
        if (currentPage > 0) {
            ItemStack prev = createGuiItem(Material.ARROW, "Previous Page");
            inventory.setItem(PREV_PAGE_SLOT, prev);
        } else {
            inventory.setItem(PREV_PAGE_SLOT, background);
        }

        if (endIndex < currentOrders.size()) {
            ItemStack next = createGuiItem(Material.ARROW, "Next Page");
            inventory.setItem(NEXT_PAGE_SLOT, next);
        } else {
            inventory.setItem(NEXT_PAGE_SLOT, background);
        }

        // Back button
        ItemStack back = createGuiItem(Material.BARRIER, "Back to Main Menu");
        inventory.setItem(BACK_SLOT, back);
    }

    private ItemStack createSellOrderItem(SellOrder order) {
        return createGuiItem(Material.LIME_DYE,
            "Sell: " + order.itemId(),
            "Quantity: " + order.quantity(),
            "Price/unit: " + order.pricePerUnit(),
            "Total: " + order.getTotalPrice(),
            "",
            NamedTextColor.YELLOW + "Click to cancel order",
            NamedTextColor.GRAY + "ID: " + order.id());
    }

    private ItemStack createBuyOrderItem(BuyOrder order) {
        return createGuiItem(Material.ORANGE_DYE,
            "Buy: " + order.itemId(),
            "Quantity: " + order.quantity(),
            "Price/unit: " + order.pricePerUnit(),
            "Total: " + order.getTotalPrice(),
            "",
            NamedTextColor.YELLOW + "Click to cancel order",
            NamedTextColor.GRAY + "ID: " + order.id());
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

        if (slot == TOGGLE_VIEW_SLOT) {
            viewingSellOrders = !viewingSellOrders;
            currentPage = 0;
            setupInterface();
        } else if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            setupInterface();
        } else if (slot == NEXT_PAGE_SLOT) {
            List<?> currentOrders = viewingSellOrders ? sellOrders : buyOrders;
            if ((currentPage + 1) * ORDERS_PER_PAGE < currentOrders.size()) {
                currentPage++;
                setupInterface();
            }
        } else if (slot == BACK_SLOT) {
            close();
            new AuctionHouseMainGUI(plugin, player, allowCollection).open();
        } else {
            // Check if clicked on an order
            ItemStack clicked = inventory.getItem(slot);
            if (clicked != null && (clicked.getType().equals(Material.LIME_DYE) || clicked.getType().equals(Material.ORANGE_DYE))) {
                handleOrderCancel(slot);
            }
        }
    }

    private void handleOrderCancel(int slot) {
        int displayIndex = getDisplaySlotIndex(slot);
        if (displayIndex < 0) return;

        int startIndex = currentPage * ORDERS_PER_PAGE;
        int orderIndex = startIndex + displayIndex;

        if (viewingSellOrders) {
            if (orderIndex < sellOrders.size()) {
                SellOrder order = sellOrders.get(orderIndex);
                auctionHouse.cancelSellOrder(player.getUniqueId(), order.id()).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage(Component.text("Sell order cancelled! Item sent to collection.", NamedTextColor.GREEN));
                            loadOrders();
                        } else {
                            player.sendMessage(Component.text("Failed to cancel order.", NamedTextColor.RED));
                        }
                    });
                });
            }
        } else {
            if (orderIndex < buyOrders.size()) {
                BuyOrder order = buyOrders.get(orderIndex);
                auctionHouse.cancelBuyOrder(player.getUniqueId(), order.id()).thenAccept(success -> {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (success) {
                            player.sendMessage(Component.text("Buy order cancelled! Money refunded to collection.", NamedTextColor.GREEN));
                            loadOrders();
                        } else {
                            player.sendMessage(Component.text("Failed to cancel order.", NamedTextColor.RED));
                        }
                    });
                });
            }
        }
    }

    private int getDisplaySlotIndex(int slot) {
        if (slot < 10 || slot >= 37) return -1;

        int row = (slot - 10) / 9;
        int col = (slot - 10) % 9;

        if (col >= 7) return -1;

        return row * 7 + col;
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

