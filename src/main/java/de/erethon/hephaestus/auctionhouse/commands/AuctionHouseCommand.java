package de.erethon.hephaestus.auctionhouse.commands;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.auctionhouse.AuctionHouseManager;
import de.erethon.hephaestus.auctionhouse.BuyOrder;
import de.erethon.hephaestus.auctionhouse.CollectableItem;
import de.erethon.hephaestus.auctionhouse.SellOrder;
import de.erethon.hephaestus.auctionhouse.gui.AuctionHouseMainGUI;
import de.erethon.hephaestus.items.HItem;
import de.erethon.hephaestus.items.HItemStack;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

/**
 * Command for interacting with the auction house.
 * Usage:
 * - /ah - Open auction house GUI (browse only)
 * - /ah open - Open auction house GUI at a trading post (with collection access)
 * - /ah sell <price> - Sell the item in your hand
 * - /ah buy <itemId> <quantity> <price> - Create a buy order
 * - /ah list <itemId> - List all orders for an item
 * - /ah myorders - List your active orders
 * - /ah cancel sell <orderId> - Cancel a sell order
 * - /ah cancel buy <orderId> - Cancel a buy order
 * - /ah collect - View collectables (items and money to collect)
 */
public class AuctionHouseCommand extends Command {

    private final Hephaestus plugin;
    private final AuctionHouseManager auctionHouse;

    public AuctionHouseCommand(Hephaestus plugin, AuctionHouseManager auctionHouse) {
        super("ah");
        this.plugin = plugin;
        this.auctionHouse = auctionHouse;
        setDescription("Auction house commands");
        setUsage("/ah [open|sell|buy|list|myorders|cancel|collect]");
        setAliases(List.of("auctionhouse", "market"));
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use this command", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            // Open GUI without collection access
            new AuctionHouseMainGUI(plugin, player, false).open();
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "open" -> {
                // Open GUI with collection access (at trading post)
                new AuctionHouseMainGUI(plugin, player, true).open();
            }
            case "sell" -> handleSell(player, args);
            case "buy" -> handleBuy(player, args);
            case "list" -> handleList(player, args);
            case "myorders" -> handleMyOrders(player);
            case "cancel" -> handleCancel(player, args);
            case "collect" -> handleCollect(player);
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleSell(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /ah sell <price_per_unit>", NamedTextColor.RED));
            return;
        }

        ItemStack handItem = player.getInventory().getItemInMainHand();
        if (handItem.isEmpty()) {
            player.sendMessage(Component.text("You must hold an item to sell", NamedTextColor.RED));
            return;
        }

        int pricePerUnit;
        try {
            pricePerUnit = Integer.parseInt(args[1]);
            if (pricePerUnit <= 0) {
                player.sendMessage(Component.text("Price must be positive", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid price", NamedTextColor.RED));
            return;
        }

        HItemStack hStack = plugin.getLibrary().get(handItem);
        if (hStack == null) {
            player.sendMessage(Component.text("This item cannot be sold", NamedTextColor.RED));
            return;
        }

        // Remove item from inventory
        player.getInventory().setItemInMainHand(null);

        auctionHouse.createSellOrder(player.getUniqueId(), hStack, pricePerUnit)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("Sell order created for " +
                                hStack.getVanillaStack().getCount() + "x at " + pricePerUnit + " each",
                                NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Failed to create sell order: " + result.message(),
                                NamedTextColor.RED));
                        // Return item
                        player.getInventory().addItem(handItem);
                    }
                });
    }

    private void handleBuy(Player player, String[] args) {
        if (args.length < 4) {
            player.sendMessage(Component.text("Usage: /ah buy <itemId> <quantity> <price_per_unit>", NamedTextColor.RED));
            return;
        }

        String itemId = args[1];
        HItem item = plugin.getLibrary().get(itemId);
        if (item == null) {
            player.sendMessage(Component.text("Unknown item: " + itemId, NamedTextColor.RED));
            return;
        }

        int quantity;
        int pricePerUnit;
        try {
            quantity = Integer.parseInt(args[2]);
            pricePerUnit = Integer.parseInt(args[3]);
            if (quantity <= 0 || pricePerUnit <= 0) {
                player.sendMessage(Component.text("Quantity and price must be positive", NamedTextColor.RED));
                return;
            }
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid quantity or price", NamedTextColor.RED));
            return;
        }

        // TODO: Check if player has enough money
        long totalCost = (long) quantity * pricePerUnit;

        auctionHouse.createBuyOrder(player.getUniqueId(), itemId, "", quantity, pricePerUnit)
                .thenAccept(result -> {
                    if (result.success()) {
                        player.sendMessage(Component.text("Buy order created for " + quantity + "x " +
                                itemId + " at " + pricePerUnit + " each (Total: " + totalCost + ")",
                                NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("Failed to create buy order: " + result.message(),
                                NamedTextColor.RED));
                    }
                });
    }

    private void handleList(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage(Component.text("Usage: /ah list <itemId>", NamedTextColor.RED));
            return;
        }

        String itemId = args[1];
        String upgrades = args.length > 2 ? args[2] : "";

        player.sendMessage(Component.text("=== Sell Orders for " + itemId + " ===", NamedTextColor.GOLD));
        auctionHouse.getSellOrders(itemId, upgrades).thenAccept(sellOrders -> {
            if (sellOrders.isEmpty()) {
                player.sendMessage(Component.text("No sell orders", NamedTextColor.GRAY));
            } else {
                for (SellOrder order : sellOrders) {
                    player.sendMessage(Component.text(order.quantity() + "x @ " + order.pricePerUnit() +
                            " each (Total: " + order.getTotalPrice() + ")", NamedTextColor.WHITE));
                }
            }
        });

        player.sendMessage(Component.text("=== Buy Orders for " + itemId + " ===", NamedTextColor.GOLD));
        auctionHouse.getBuyOrders(itemId, upgrades).thenAccept(buyOrders -> {
            if (buyOrders.isEmpty()) {
                player.sendMessage(Component.text("No buy orders", NamedTextColor.GRAY));
            } else {
                for (BuyOrder order : buyOrders) {
                    player.sendMessage(Component.text(order.quantity() + "x @ " + order.pricePerUnit() +
                            " each (Total: " + order.getTotalPrice() + ")", NamedTextColor.WHITE));
                }
            }
        });
    }

    private void handleMyOrders(Player player) {
        player.sendMessage(Component.text("=== Your Sell Orders ===", NamedTextColor.GOLD));
        auctionHouse.getPlayerSellOrders(player.getUniqueId()).thenAccept(sellOrders -> {
            if (sellOrders.isEmpty()) {
                player.sendMessage(Component.text("No active sell orders", NamedTextColor.GRAY));
            } else {
                for (SellOrder order : sellOrders) {
                    player.sendMessage(Component.text("[" + order.id() + "] " +
                            order.itemId() + " - " + order.quantity() + "x @ " + order.pricePerUnit(),
                            NamedTextColor.WHITE));
                }
            }
        });

        player.sendMessage(Component.text("=== Your Buy Orders ===", NamedTextColor.GOLD));
        auctionHouse.getPlayerBuyOrders(player.getUniqueId()).thenAccept(buyOrders -> {
            if (buyOrders.isEmpty()) {
                player.sendMessage(Component.text("No active buy orders", NamedTextColor.GRAY));
            } else {
                for (BuyOrder order : buyOrders) {
                    player.sendMessage(Component.text("[" + order.id() + "] " +
                            order.itemId() + " - " + order.quantity() + "x @ " + order.pricePerUnit(),
                            NamedTextColor.WHITE));
                }
            }
        });
    }

    private void handleCancel(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage(Component.text("Usage: /ah cancel <sell|buy> <orderId>", NamedTextColor.RED));
            return;
        }

        long orderId;
        try {
            orderId = Long.parseLong(args[2]);
        } catch (NumberFormatException e) {
            player.sendMessage(Component.text("Invalid order ID", NamedTextColor.RED));
            return;
        }

        if (args[1].equalsIgnoreCase("sell")) {
            auctionHouse.cancelSellOrder(player.getUniqueId(), orderId).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.text("Sell order cancelled. Item sent to collection.",
                            NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to cancel order. Order not found or not yours.",
                            NamedTextColor.RED));
                }
            });
        } else if (args[1].equalsIgnoreCase("buy")) {
            auctionHouse.cancelBuyOrder(player.getUniqueId(), orderId).thenAccept(success -> {
                if (success) {
                    player.sendMessage(Component.text("Buy order cancelled. Money refunded to collection.",
                            NamedTextColor.GREEN));
                } else {
                    player.sendMessage(Component.text("Failed to cancel order. Order not found or not yours.",
                            NamedTextColor.RED));
                }
            });
        } else {
            player.sendMessage(Component.text("Usage: /ah cancel <sell|buy> <orderId>", NamedTextColor.RED));
        }
    }

    private void handleCollect(Player player) {
        player.sendMessage(Component.text("=== Items to Collect ===", NamedTextColor.GOLD));
        auctionHouse.getCollectableItems(player.getUniqueId()).thenAccept(items -> {
            if (items.isEmpty()) {
                player.sendMessage(Component.text("No items to collect", NamedTextColor.GRAY));
            } else {
                for (CollectableItem item : items) {
                    player.sendMessage(Component.text("[" + item.id() + "] " +
                            item.itemId() + " x" + item.quantity(), NamedTextColor.WHITE));
                }
                player.sendMessage(Component.text("Use a GUI or custom collection system to collect items",
                        NamedTextColor.YELLOW));
            }
        });

        player.sendMessage(Component.text("=== Money to Collect ===", NamedTextColor.GOLD));
        auctionHouse.getCollectableMoney(player.getUniqueId()).thenAccept(money -> {
            if (money == 0) {
                player.sendMessage(Component.text("No money to collect", NamedTextColor.GRAY));
            } else {
                player.sendMessage(Component.text("Money: " + money, NamedTextColor.WHITE));
                player.sendMessage(Component.text("Use a GUI or custom collection system to collect money",
                        NamedTextColor.YELLOW));
            }
        });
    }

    private void sendHelp(Player player) {
        player.sendMessage(Component.text("=== Auction House Commands ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/ah - Open auction house GUI", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah open - Open at trading post (with collection)", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah sell <price> - Sell item in hand", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah buy <itemId> <qty> <price> - Create buy order", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah list <itemId> - List all orders for item", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah myorders - List your active orders", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah cancel <sell|buy> <orderId> - Cancel order", NamedTextColor.YELLOW));
        player.sendMessage(Component.text("/ah collect - View items/money to collect", NamedTextColor.YELLOW));
    }
}

