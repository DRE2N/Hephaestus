package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.SellOrder;
import de.erethon.hephaestus.items.HItem;
import de.erethon.tyche.TychePlugin;
import de.erethon.tyche.models.OwnerType;
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
 * GUI for instant buying from a sell order.
 * Allows selecting quantity and confirming purchase.
 */
public class InstantBuyGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final HItem item;
    private final SellOrder order;
    private final boolean allowCollection;

    private int selectedQuantity = 1;

    private static final int ITEM_DISPLAY_SLOT = 13;
    private static final int DECREASE_SLOT = 29;
    private static final int QUANTITY_DISPLAY_SLOT = 31;
    private static final int INCREASE_SLOT = 33;
    private static final int CONFIRM_SLOT = 40;
    private static final int CANCEL_SLOT = 44;

    public InstantBuyGUI(Hephaestus plugin, Player player, HItem item, SellOrder order, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.item = item;
        this.order = order;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Instant Buy", NamedTextColor.GOLD));

        setupInterface();
        Bukkit.getPluginManager().registerEvents(this, plugin);
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
        lore.add(Component.text("Price per unit: " + order.pricePerUnit(), NamedTextColor.GOLD));
        lore.add(Component.text("Available: " + order.quantity(), NamedTextColor.YELLOW));
        if (meta.hasLore() && meta.lore() != null) {
            lore.add(Component.empty());
            lore.addAll(meta.lore());
        }
        meta.lore(lore);
        itemDisplay.setItemMeta(meta);
        inventory.setItem(ITEM_DISPLAY_SLOT, itemDisplay);

        // Quantity controls
        ItemStack decrease = createGuiItem(Material.RED_CONCRETE, "-1", "Click to decrease quantity");
        inventory.setItem(DECREASE_SLOT, decrease);

        long totalCost = (long) selectedQuantity * order.pricePerUnit();
        ItemStack quantityDisplay = createGuiItem(Material.PAPER,
            "Quantity: " + selectedQuantity,
            "Price per unit: " + order.pricePerUnit(),
            "Total cost: " + totalCost);
        inventory.setItem(QUANTITY_DISPLAY_SLOT, quantityDisplay);

        ItemStack increase = createGuiItem(Material.LIME_CONCRETE, "+1", "Click to increase quantity");
        inventory.setItem(INCREASE_SLOT, increase);

        // Confirm button
        ItemStack confirm = createGuiItem(Material.EMERALD_BLOCK,
            "Confirm Purchase",
            "Buy " + selectedQuantity + "x for " + totalCost,
            "",
            allowCollection ? NamedTextColor.GREEN + "Items will go to collection"
                           : NamedTextColor.YELLOW + "Visit trading post to collect");
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Cancel button
        ItemStack cancel = createGuiItem(Material.BARRIER, "Cancel", "Return to orders");
        inventory.setItem(CANCEL_SLOT, cancel);
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

        if (slot == DECREASE_SLOT) {
            if (selectedQuantity > 1) {
                selectedQuantity--;
                setupInterface();
            }
        } else if (slot == INCREASE_SLOT) {
            if (selectedQuantity < order.quantity()) {
                selectedQuantity++;
                setupInterface();
            }
        } else if (slot == CONFIRM_SLOT) {
            if (!(Bukkit.getPluginManager().isPluginEnabled("Tyche"))) {
                player.sendMessage(Component.text("Instant Buy not available", NamedTextColor.RED));
                return;
            }
            long totalCost = (long) selectedQuantity * order.pricePerUnit();
            TychePlugin.getEconomyService().getBalance(player.getUniqueId(), OwnerType.PLAYER, "herone")
                .thenAccept(balance -> {
                    if (balance < totalCost) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                            player.sendMessage(Component.text("Insufficient funds for this purchase.", NamedTextColor.RED))
                        );
                    } else {
                        auctionHouse.instantBuy(player.getUniqueId(), item.getKey().toString(), "", selectedQuantity)
                                .thenAccept(result -> {
                                    Bukkit.getScheduler().runTask(plugin, () -> {
                                        if (result.success()) {
                                            player.sendMessage(Component.text("Purchase successful! Items sent to collection.", NamedTextColor.GREEN));
                                            close();
                                            new AuctionHouseMainGUI(plugin, player, allowCollection).open();
                                        } else {
                                            player.sendMessage(Component.text("Purchase failed: " + result.message(), NamedTextColor.RED));
                                        }
                                    });
                                });
                    }
                });
        } else if (slot == CANCEL_SLOT) {
            close();
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

