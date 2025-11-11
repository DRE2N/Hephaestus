package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.jobs.crafting.gui.GUIUtils;
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
 * GUI for selling items from player's inventory.
 * Players can select items and set prices to create sell orders.
 */
public class SellItemsGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final boolean allowCollection;

    private int pricePerUnit = 1;

    // Slot positions - 4 rows layout (36 slots)
    private static final int SELECTED_ITEM_SLOT = 13;
    private static final int PRICE_DISPLAY_SLOT = 22;
    private static final int PRICE_DOWN_SLOT = 21;
    private static final int PRICE_UP_SLOT = 23;
    private static final int CONFIRM_SLOT = 31;
    private static final int BACK_SLOT = 27;

    public SellItemsGUI(Hephaestus plugin, Player player, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 36,
            Component.text("Sell Items", NamedTextColor.GOLD));

        setupInterface();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void setupInterface() {
        inventory.clear();

        // Instructions
        ItemStack instructions = createGuiItem(Material.BOOK,
            "How to Sell",
            "1. Place item in the slot",
            "2. Adjust price with +/- buttons",
            "3. Click confirm to list");
        inventory.setItem(4, instructions);

        // Selected item slot - leave empty for player to place item
        // (Player can freely place/remove items here)

        // Price controls
        ItemStack priceDown = createGuiItem(Material.RED_CONCRETE,
            "Decrease Price",
            "Click: -1",
            "Shift-Click: -10");
        inventory.setItem(PRICE_DOWN_SLOT, priceDown);

        updatePriceDisplay();

        ItemStack priceUp = createGuiItem(Material.GREEN_CONCRETE,
            "Increase Price",
            "Click: +1",
            "Shift-Click: +10");
        inventory.setItem(PRICE_UP_SLOT, priceUp);

        // Confirm button
        ItemStack confirm = createGuiItem(Material.EMERALD_BLOCK,
            "Confirm & List",
            "Place an item and click to list");
        inventory.setItem(CONFIRM_SLOT, confirm);

        // Back button
        ItemStack back = createGuiItem(Material.ARROW, "Back to Main Menu");
        inventory.setItem(BACK_SLOT, back);
    }

    private void updatePriceDisplay() {
        ItemStack priceDisplay = createGuiItem(Material.GOLD_INGOT,
            "Price per Unit",
            pricePerUnit + " coins");
        // Set the item count to show the price visually. Let's hope Mojang does not suddenly enforce the stack limit client-side.
        priceDisplay.setAmount(Math.min(Math.max(1, pricePerUnit), 9999));
        inventory.setItem(PRICE_DISPLAY_SLOT, priceDisplay);
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

        int slot = event.getRawSlot();

        // Allow normal interaction with the item slot
        if (slot == SELECTED_ITEM_SLOT) {
            return;
        }

        // Cancel all other clicks in the GUI
        if (slot >= 0 && slot < 36) {
            event.setCancelled(true);

            if (slot == PRICE_DOWN_SLOT) {
                // Decrease price
                int amount = event.isShiftClick() ? 10 : 1;
                pricePerUnit = Math.max(1, pricePerUnit - amount);
                updatePriceDisplay();
            } else if (slot == PRICE_UP_SLOT) {
                // Increase price
                int amount = event.isShiftClick() ? 10 : 1;
                pricePerUnit = Math.min(1000000, pricePerUnit + amount);
                updatePriceDisplay();
            } else if (slot == CONFIRM_SLOT) {
                // Get the item from the slot
                ItemStack selectedItem = inventory.getItem(SELECTED_ITEM_SLOT);

                if (selectedItem == null || selectedItem.getType().equals(Material.AIR)) {
                    player.sendMessage(Component.text("✗ Please place an item in the slot first!", NamedTextColor.RED));
                    return;
                }

                // Create sell order
                player.sendMessage(Component.text("Creating sell order...", NamedTextColor.YELLOW));

                // Convert to HItemStack
                de.erethon.hephaestus.items.HItemStack hItemStack = plugin.getLibrary().get(selectedItem);

                if (hItemStack == null) {
                    player.sendMessage(Component.text("Error: This item cannot be sold on the auction house!", NamedTextColor.RED));
                    return;
                }

                // Remove item from slot and create order
                ItemStack itemToSell = selectedItem.clone();
                inventory.setItem(SELECTED_ITEM_SLOT, null);

                auctionHouse.createSellOrder(player.getUniqueId(), hItemStack, pricePerUnit)
                    .thenAccept(result -> Bukkit.getScheduler().runTask(plugin, () -> {
                        if (result.success()) {
                            player.sendMessage(Component.text("✓ Sell order created successfully!", NamedTextColor.GREEN));
                            player.sendMessage(Component.text("Listed " + itemToSell.getAmount() + "x " +
                                hItemStack.getItem().getKey() + " for " + pricePerUnit + " coins each", NamedTextColor.GRAY));
                        } else {
                            player.sendMessage(Component.text("✗ Failed to create sell order: " + result.message(), NamedTextColor.RED));
                            // Return item to player on failure
                            player.getInventory().addItem(itemToSell);
                        }
                    }))
                    .exceptionally(throwable -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            player.sendMessage(Component.text("✗ Error creating sell order: " + throwable.getMessage(), NamedTextColor.RED));
                            // Return item to player on error
                            player.getInventory().addItem(itemToSell);
                        });
                        return null;
                    });

                close();
            } else if (slot == BACK_SLOT) {
                // Return any item in the slot to player
                ItemStack selectedItem = inventory.getItem(SELECTED_ITEM_SLOT);
                if (selectedItem != null && !selectedItem.getType().equals(Material.AIR)) {
                    player.getInventory().addItem(selectedItem);
                }
                close();
                new AuctionHouseMainGUI(plugin, player, allowCollection).open();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().equals(inventory)) {
            // Return any item in the slot to player
            ItemStack selectedItem = inventory.getItem(SELECTED_ITEM_SLOT);
            if (selectedItem != null && !selectedItem.getType().equals(Material.AIR)) {
                player.getInventory().addItem(selectedItem);
            }
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

