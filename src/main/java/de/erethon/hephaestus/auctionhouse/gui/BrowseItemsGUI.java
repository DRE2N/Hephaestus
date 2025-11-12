    package de.erethon.hephaestus.auctionhouse.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.items.HItem;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
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
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * GUI for browsing and buying items from the auction house.
 */
public class BrowseItemsGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;
    private final Player player;
    private final Inventory inventory;
    private final boolean allowCollection;

    private final List<HItem> availableItems = new ArrayList<>();
    private final List<String> categories = new ArrayList<>();
    private String selectedCategory = null;
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for items

    // Bottom row slots for navigation
    private static final int PREV_PAGE_SLOT = 45;
    private static final int BACK_SLOT = 49;
    private static final int NEXT_PAGE_SLOT = 53;
    // Category filter slots (top of bottom row)
    private static final int CATEGORY_START_SLOT = 46;

    public BrowseItemsGUI(Hephaestus plugin, Player player, boolean allowCollection) {
        this.plugin = plugin;
        this.auctionHouse = plugin.getAuctionHouseManager();
        this.player = player;
        this.allowCollection = allowCollection;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text("Browse Items ", NamedTextColor.GOLD).append(Component.object(ObjectContents.sprite(Key.key("item/ender_eye")))));

        loadAvailableItems();
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private void loadAvailableItems() {
        // Get distinct item IDs that have active listings
        auctionHouse.getDistinctListedItemIds().thenAccept(itemIds -> {
            availableItems.clear();
            categories.clear();
            Set<String> categorySet = new HashSet<>();

            for (String itemId : itemIds) {
                HItem item = plugin.getLibrary().get(itemId);
                if (item != null) {
                    availableItems.add(item);
                    // Collect categories from tags
                    categorySet.addAll(item.getTags());
                }
            }

            categories.addAll(categorySet);
            categories.sort(String::compareTo);

            // Update GUI on main thread
            Bukkit.getScheduler().runTask(plugin, this::setupInterface);
        });
    }

    private void setupInterface() {
        inventory.clear();

        // Filter items by category if one is selected
        List<HItem> displayItems = availableItems;
        if (selectedCategory != null) {
            displayItems = availableItems.stream()
                .filter(item -> item.getTags().contains(selectedCategory))
                .collect(Collectors.toList());
        }

        // Display items for current page (using first 5 rows)
        int startIndex = currentPage * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, displayItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            HItem item = displayItems.get(i);
            ItemStack displayItem = createItemDisplay(item);
            inventory.setItem(i - startIndex, displayItem);
        }

        // Bottom row - navigation and controls
        // Previous page button
        if (currentPage > 0) {
            ItemStack prevPage = createGuiItem(Material.ARROW, "Previous Page");
            inventory.setItem(PREV_PAGE_SLOT, prevPage);
        }

        // Category filter indicator
        if (selectedCategory != null) {
            ItemStack categoryFilter = createGuiItem(Material.NAME_TAG,
                "Filter: " + selectedCategory,
                "Click to clear filter");
            inventory.setItem(CATEGORY_START_SLOT, categoryFilter);
        } else if (!categories.isEmpty()) {
            ItemStack categoryFilter = createGuiItem(Material.NAME_TAG,
                "All Categories",
                "Click to filter by category");
            inventory.setItem(CATEGORY_START_SLOT, categoryFilter);
        }

        // Back button
        ItemStack backItem = createGuiItem(Material.BARRIER, "Back to Main Menu");
        inventory.setItem(BACK_SLOT, backItem);

        // Next page button
        if (endIndex < displayItems.size()) {
            ItemStack nextPage = createGuiItem(Material.ARROW, "Next Page");
            inventory.setItem(NEXT_PAGE_SLOT, nextPage);
        }
    }

    private ItemStack createItemDisplay(HItem item) {
        ItemStack display = item.createStack().getBukkitStack().clone();
        ItemMeta meta = display.getItemMeta();
        List<Component> lore = new ArrayList<>();

        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }

        lore.add(Component.empty());
        lore.add(Component.text("Click to view listings", NamedTextColor.YELLOW));

        meta.lore(lore);
        display.setItemMeta(meta);
        return display;
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

        // Filter items by category if one is selected
        List<HItem> displayItems = availableItems;
        if (selectedCategory != null) {
            displayItems = availableItems.stream()
                .filter(item -> item.getTags().contains(selectedCategory))
                .collect(Collectors.toList());
        }

        if (slot == PREV_PAGE_SLOT && currentPage > 0) {
            currentPage--;
            setupInterface();
        } else if (slot == NEXT_PAGE_SLOT) {
            if ((currentPage + 1) * ITEMS_PER_PAGE < displayItems.size()) {
                currentPage++;
                setupInterface();
            }
        } else if (slot == BACK_SLOT) {
            close();
            new AuctionHouseMainGUI(plugin, player, allowCollection).open();
        } else if (slot == CATEGORY_START_SLOT) {
            // Cycle through categories or clear filter
            if (selectedCategory == null && !categories.isEmpty()) {
                selectedCategory = categories.get(0);
            } else if (selectedCategory != null) {
                int currentIndex = categories.indexOf(selectedCategory);
                if (currentIndex >= 0 && currentIndex < categories.size() - 1) {
                    selectedCategory = categories.get(currentIndex + 1);
                } else {
                    selectedCategory = null; // Clear filter
                }
            }
            currentPage = 0;
            setupInterface();
        } else if (slot < ITEMS_PER_PAGE) {
            // Check if clicked on an item (first 5 rows)
            ItemStack clicked = inventory.getItem(slot);
            if (clicked != null && !clicked.getType().equals(Material.AIR)) {
                int startIndex = currentPage * ITEMS_PER_PAGE;
                int itemIndex = startIndex + slot;

                if (itemIndex < displayItems.size()) {
                    HItem item = displayItems.get(itemIndex);
                    close();
                    new ItemOrdersGUI(plugin, player, item, allowCollection).open();
                }
            }
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

