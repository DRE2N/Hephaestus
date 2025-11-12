package de.erethon.hephaestus.shops.gui;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.shops.Shop;
import de.erethon.hephaestus.shops.ShopManager;
import de.erethon.tyche.TychePlugin;
import de.erethon.tyche.models.OwnerType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.TooltipDisplay;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * GUI for browsing and buying/selling items in a shop.
 */
public class ShopGUI implements InventoryHolder, Listener {

    private final Hephaestus plugin;
    private final ShopManager shopManager;
    private final Player player;
    private final Shop shop;
    private final Inventory inventory;
    private final Map<Integer, Shop.ShopItem> slotToItem = new HashMap<>();
    private final Map<String, Integer> playerStock = new HashMap<>();
    private long playerBalance = 0;

    private static final int ITEMS_PER_PAGE = 45; // 5 rows for items
    private static final int CLOSE_SLOT = 49;

    public ShopGUI(Hephaestus plugin, Player player, Shop shop) {
        this.plugin = plugin;
        this.shopManager = plugin.getShopManager();
        this.player = player;
        this.shop = shop;
        this.inventory = Bukkit.createInventory(this, 54,
            Component.text(shop.getDisplayName(), NamedTextColor.GOLD));

        Bukkit.getPluginManager().registerEvents(this, plugin);
        loadPlayerStock();
    }

    private void loadPlayerStock() {
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (Shop.ShopItem item : shop.getItems()) {
            var future = shopManager.getPlayerStock(
                player.getUniqueId(),
                shop.getId(),
                item.getItemId(),
                item
            ).thenAccept(stock -> {
                playerStock.put(item.getItemId(), stock);
            });
            futures.add(future);
        }

        // Also fetch player balance
        if (Bukkit.getPluginManager().isPluginEnabled("Tyche")) {
            var balanceFuture = TychePlugin.getEconomyService()
                .getBalance(player.getUniqueId(), OwnerType.PLAYER, "herone")
                .thenAccept(balance -> {
                    playerBalance = balance;
                });
            futures.add(balanceFuture);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> Bukkit.getScheduler().runTask(plugin, this::setupInterface));
    }

    private void setupInterface() {
        inventory.clear();
        slotToItem.clear();

        // Background
        ItemStack background = createGuiItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        background.setData(DataComponentTypes.TOOLTIP_DISPLAY, TooltipDisplay.tooltipDisplay().hideTooltip(true).build());
        for (int i = 45; i < 54; i++) {
            inventory.setItem(i, background);
        }

        // Display items (first 5 rows)
        int slot = 0;
        for (Shop.ShopItem shopItem : shop.getItems()) {
            if (slot >= ITEMS_PER_PAGE) break;

            ItemStack display = createItemDisplay(shopItem);
            inventory.setItem(slot, display);
            slotToItem.put(slot, shopItem);
            slot++;
        }

        // Close button
        ItemStack close = createGuiItem(Material.BARRIER, "Close");
        inventory.setItem(CLOSE_SLOT, close);
    }

    private ItemStack createItemDisplay(Shop.ShopItem shopItem) {
        ItemStack display = shopItem.getHItem().createStack().getBukkitStack().clone();
        ItemMeta meta = display.getItemMeta();

        List<Component> lore = new ArrayList<>();
        if (meta.hasLore() && meta.lore() != null) {
            lore.addAll(meta.lore());
        }

        lore.add(Component.empty());

        Shop.TransactionType type = shopItem.getTransactionType();

        if (type == Shop.TransactionType.BUY || type == Shop.TransactionType.BOTH) {
            // Color price based on whether player can afford it
            boolean canAfford = playerBalance >= shopItem.getBuyPrice();
            NamedTextColor priceColor = canAfford ? NamedTextColor.GREEN : NamedTextColor.RED;
            lore.add(Component.text("Buy Price: " + shopItem.getBuyPrice() + " Herone", priceColor));

            int stock = playerStock.getOrDefault(shopItem.getItemId(), 0);
            if (shopItem.hasInfiniteStock()) {
                lore.add(Component.text("Stock: Unlimited", NamedTextColor.GRAY));
            } else {
                lore.add(Component.text("Your Stock: " + stock, stock > 0 ? NamedTextColor.GRAY : NamedTextColor.RED));
            }
        }

        if (type == Shop.TransactionType.SELL || type == Shop.TransactionType.BOTH) {
            if (type == Shop.TransactionType.BOTH) {
                lore.add(Component.empty());
            }
            lore.add(Component.text("Sell Price: " + shopItem.getSellPrice() + " Herone", NamedTextColor.GOLD));
        }

        lore.add(Component.empty());
        lore.add(Component.text("Left-Click to Buy", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Right-Click to Sell", NamedTextColor.DARK_GRAY));

        List<Component> lore2 = new ArrayList<>();
        for (Component component : lore) {
            component = component.decoration(TextDecoration.ITALIC, false);
            lore2.add(component);
        }
        meta.lore(lore2);
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
    @NotNull
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

        if (slot == CLOSE_SLOT) {
            close();
            return;
        }

        Shop.ShopItem shopItem = slotToItem.get(slot);
        if (shopItem == null) return;

        ClickType clickType = event.getClick();

        if (clickType == ClickType.LEFT || clickType == ClickType.SHIFT_LEFT) {
            handleBuy(shopItem, clickType == ClickType.SHIFT_LEFT);
        }
        else if (clickType == ClickType.RIGHT || clickType == ClickType.SHIFT_RIGHT) {
            handleSell(shopItem, clickType == ClickType.SHIFT_RIGHT);
        }
    }

    private void handleBuy(Shop.ShopItem shopItem, boolean buyStack) {
        Shop.TransactionType type = shopItem.getTransactionType();
        if (type != Shop.TransactionType.BUY && type != Shop.TransactionType.BOTH) {
            player.sendMessage(Component.text("This item is not for sale.", NamedTextColor.RED));
            return;
        }

        int amount = buyStack ? 64 : 1;
        int stock = playerStock.getOrDefault(shopItem.getItemId(), 0);

        if (!shopItem.hasInfiniteStock() && stock < amount) {
            player.sendMessage(Component.text("Shop sold out! Available: " + stock + " - Try again at a different time.", NamedTextColor.RED));
            return;
        }

        long totalPrice = shopItem.getBuyPrice() * amount;

        if (!Bukkit.getPluginManager().isPluginEnabled("Tyche")) {
            player.sendMessage(Component.text("Economy service is not available!", NamedTextColor.RED));
            return;
        }

        if (player.getInventory().firstEmpty() == -1) {
            player.sendMessage(Component.text("Your inventory is full!", NamedTextColor.RED));
            return;
        }

        TychePlugin.getEconomyService().getBalance(player.getUniqueId(), OwnerType.PLAYER, "herone")
            .thenAccept(balance -> {
                if (balance < totalPrice) {
                    Bukkit.getScheduler().runTask(plugin, () ->
                        player.sendMessage(Component.text("Not enough money! Need: " + totalPrice + ", Have: " + balance, NamedTextColor.RED))
                    );
                    return;
                }

                // Withdraw money
                TychePlugin.getEconomyService().withdraw(player.getUniqueId(), OwnerType.PLAYER, "herone", totalPrice,
                    "Bought " + amount + "x " + shopItem.getItemId() + " from " + shop.getDisplayName(), player.getUniqueId())
                    .thenRun(() -> {
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            // Update balance
                            playerBalance -= totalPrice;

                            // Give item
                            ItemStack itemToGive = shopItem.getHItem().createStack().getBukkitStack().clone();
                            itemToGive.setAmount(amount);
                            player.getInventory().addItem(itemToGive);

                            // Update stock
                            if (!shopItem.hasInfiniteStock()) {
                                shopManager.decreasePlayerStock(player.getUniqueId(), shop.getId(), shopItem.getItemId(), amount)
                                    .thenRun(() -> {
                                        playerStock.put(shopItem.getItemId(), stock - amount);
                                        Bukkit.getScheduler().runTask(plugin, this::setupInterface);
                                    });
                            } else {
                                setupInterface();
                            }

                            player.sendMessage(Component.text("Purchased " + amount + "x " + shopItem.getItemId() + " for " + totalPrice + " Herone!", NamedTextColor.GREEN));
                        });
                    });
            });
    }

    private void handleSell(Shop.ShopItem shopItem, boolean sellAll) {
        Shop.TransactionType type = shopItem.getTransactionType();
        if (type != Shop.TransactionType.SELL && type != Shop.TransactionType.BOTH) {
            player.sendMessage(Component.text("You cannot sell this item!", NamedTextColor.RED));
            return;
        }

        int playerHas = 0;
        ItemStack templateStack = shopItem.getHItem().createStack().getBukkitStack();

        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(templateStack)) {
                playerHas += stack.getAmount();
            }
        }

        if (playerHas == 0) {
            player.sendMessage(Component.text("You don't have any of this item to sell!", NamedTextColor.RED));
            return;
        }

        int amount = 0;
        if (amount > playerHas) {
            amount = playerHas;
        } else {
            amount = sellAll ? playerHas : 1;
        }

        long totalPrice = shopItem.getSellPrice() * amount;

        if (!Bukkit.getPluginManager().isPluginEnabled("Tyche")) {
            player.sendMessage(Component.text("Economy service is not available!", NamedTextColor.RED));
            return;
        }

        int remaining = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(templateStack)) {
                int toRemove = Math.min(remaining, stack.getAmount());
                stack.setAmount(stack.getAmount() - toRemove);
                remaining -= toRemove;

                if (remaining <= 0) break;
            }
        }

        int finalAmount = amount;
        TychePlugin.getEconomyService().deposit(player.getUniqueId(), OwnerType.PLAYER, "herone", totalPrice,
            "Sold " + amount + "x " + shopItem.getItemId() + " to " + shop.getDisplayName(), player.getUniqueId())
            .thenRun(() -> {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Update balance
                    playerBalance += totalPrice;
                    setupInterface();
                    player.sendMessage(Component.text("Sold " + finalAmount + "x " + shopItem.getItemId() + " for " + totalPrice + " Herone!", NamedTextColor.GREEN));
                });
            });
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

