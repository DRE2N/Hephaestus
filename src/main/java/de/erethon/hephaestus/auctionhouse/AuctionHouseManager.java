package de.erethon.hephaestus.auctionhouse;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.items.HItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for the auction house system, similar to GW2's Trading Post.
 * Handles buy/sell orders and instant transactions.
 */
public class AuctionHouseManager {

    private final AuctionHouseDatabaseManager databaseManager;

    public AuctionHouseManager(Hephaestus plugin, AuctionHouseDatabaseManager databaseManager) {
        this.databaseManager = databaseManager;
    }

    /**
     * Creates a sell order for an item.
     * If there's a matching buy order with equal or higher price, it's fulfilled immediately.
     * Otherwise, the sell order is listed.
     */
    public CompletableFuture<OrderResult> createSellOrder(UUID playerUuid, HItemStack itemStack, int pricePerUnit) {
        return databaseManager.getBestBuyOrder(itemStack.getItem().getKey().toString(), serializeUpgrades(itemStack))
                .thenCompose(bestBuyOrder -> {
                    if (bestBuyOrder.isPresent() && bestBuyOrder.get().pricePerUnit() >= pricePerUnit) {
                        // Fulfill buy order immediately
                        BuyOrder order = bestBuyOrder.get();
                        return fulfillBuyOrder(order.id(), playerUuid, itemStack, pricePerUnit);
                    } else {
                        // Create sell listing
                        return databaseManager.createSellOrder(
                                playerUuid,
                                itemStack.getItem().getKey().toString(),
                                serializeUpgrades(itemStack),
                                serializeItemStack(itemStack),
                                itemStack.getVanillaStack().getCount(),
                                pricePerUnit
                        ).thenApply(orderId -> new OrderResult(true, "Sell order created", orderId));
                    }
                });
    }

    /**
     * Creates a buy order for an item.
     * If there's a matching sell order with equal or lower price, it's fulfilled immediately.
     * Otherwise, the buy order is listed.
     */
    public CompletableFuture<OrderResult> createBuyOrder(UUID playerUuid, String itemId, String upgrades, int quantity, int pricePerUnit) {
        return databaseManager.getBestSellOrder(itemId, upgrades)
                .thenCompose(bestSellOrder -> {
                    if (bestSellOrder.isPresent() && bestSellOrder.get().pricePerUnit() <= pricePerUnit) {
                        // Fulfill sell order immediately
                        SellOrder order = bestSellOrder.get();
                        return fulfillSellOrder(order.id(), playerUuid, quantity, pricePerUnit);
                    } else {
                        // Create buy listing
                        return databaseManager.createBuyOrder(
                                playerUuid,
                                itemId,
                                upgrades,
                                quantity,
                                pricePerUnit
                        ).thenApply(orderId -> new OrderResult(true, "Buy order created", orderId));
                    }
                });
    }

    /**
     * Instantly buy an item from the cheapest sell order.
     */
    public CompletableFuture<OrderResult> instantBuy(UUID playerUuid, String itemId, String upgrades, int quantity) {
        return databaseManager.getBestSellOrder(itemId, upgrades)
                .thenCompose(bestSellOrder -> {
                    if (bestSellOrder.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new OrderResult(false, "No sell orders available", -1L)
                        );
                    }
                    SellOrder order = bestSellOrder.get();
                    return fulfillSellOrder(order.id(), playerUuid, quantity, order.pricePerUnit());
                });
    }

    /**
     * Instantly sell an item to the best buy order.
     */
    public CompletableFuture<OrderResult> instantSell(UUID playerUuid, HItemStack itemStack) {
        return databaseManager.getBestBuyOrder(itemStack.getItem().getKey().toString(), serializeUpgrades(itemStack))
                .thenCompose(bestBuyOrder -> {
                    if (bestBuyOrder.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new OrderResult(false, "No buy orders available", -1L)
                        );
                    }
                    BuyOrder order = bestBuyOrder.get();
                    return fulfillBuyOrder(order.id(), playerUuid, itemStack, order.pricePerUnit());
                });
    }

    /**
     * Get all sell orders for a specific item.
     */
    public CompletableFuture<List<SellOrder>> getSellOrders(String itemId, String upgrades) {
        return databaseManager.getSellOrders(itemId, upgrades);
    }

    /**
     * Get all buy orders for a specific item.
     */
    public CompletableFuture<List<BuyOrder>> getBuyOrders(String itemId, String upgrades) {
        return databaseManager.getBuyOrders(itemId, upgrades);
    }

    /**
     * Cancel a sell order.
     */
    public CompletableFuture<Boolean> cancelSellOrder(UUID playerUuid, long orderId) {
        return databaseManager.getSellOrderById(orderId)
                .thenCompose(order -> {
                    if (order.isEmpty() || !order.get().sellerUuid().equals(playerUuid)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    // Return item to collection
                    return databaseManager.addCollectableItem(
                            playerUuid,
                            order.get().itemId(),
                            order.get().upgrades(),
                            order.get().itemData(),
                            order.get().quantity()
                    ).thenCompose(v -> databaseManager.cancelSellOrder(orderId));
                });
    }

    /**
     * Cancel a buy order.
     */
    public CompletableFuture<Boolean> cancelBuyOrder(UUID playerUuid, long orderId) {
        return databaseManager.getBuyOrderById(orderId)
                .thenCompose(order -> {
                    if (order.isEmpty() || !order.get().buyerUuid().equals(playerUuid)) {
                        return CompletableFuture.completedFuture(false);
                    }
                    // Return money to collection
                    long refundAmount = (long) order.get().quantity() * order.get().pricePerUnit();
                    return databaseManager.addCollectableMoney(playerUuid, refundAmount)
                            .thenCompose(v -> databaseManager.cancelBuyOrder(orderId));
                });
    }

    /**
     * Get all items waiting to be collected by a player.
     */
    public CompletableFuture<List<CollectableItem>> getCollectableItems(UUID playerUuid) {
        return databaseManager.getCollectableItems(playerUuid);
    }

    /**
     * Get money waiting to be collected by a player.
     */
    public CompletableFuture<Long> getCollectableMoney(UUID playerUuid) {
        return databaseManager.getCollectableMoney(playerUuid);
    }

    /**
     * Mark items as collected (to be called after player actually receives them).
     */
    public CompletableFuture<Void> markItemsCollected(List<Long> itemIds) {
        return databaseManager.markItemsCollected(itemIds);
    }

    /**
     * Mark money as collected (to be called after player actually receives it).
     */
    public CompletableFuture<Void> markMoneyCollected(UUID playerUuid) {
        return databaseManager.markMoneyCollected(playerUuid);
    }

    /**
     * Get player's active sell orders.
     */
    public CompletableFuture<List<SellOrder>> getPlayerSellOrders(UUID playerUuid) {
        return databaseManager.getPlayerSellOrders(playerUuid);
    }

    /**
     * Get player's active buy orders.
     */
    public CompletableFuture<List<BuyOrder>> getPlayerBuyOrders(UUID playerUuid) {
        return databaseManager.getPlayerBuyOrders(playerUuid);
    }

    /**
     * Get all distinct item IDs that currently have active sell orders.
     */
    public CompletableFuture<List<String>> getDistinctListedItemIds() {
        return databaseManager.getDistinctListedItemIds();
    }

    private CompletableFuture<OrderResult> fulfillBuyOrder(long buyOrderId, UUID sellerUuid, HItemStack itemStack, int pricePerUnit) {
        return databaseManager.getBuyOrderById(buyOrderId)
                .thenCompose(orderOpt -> {
                    if (orderOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new OrderResult(false, "Buy order not found", -1L)
                        );
                    }
                    BuyOrder order = orderOpt.get();
                    int quantity = Math.min(itemStack.getVanillaStack().getCount(), order.quantity());
                    long totalPrice = (long) quantity * pricePerUnit;

                    // Give money to seller
                    return databaseManager.addCollectableMoney(sellerUuid, totalPrice)
                            .thenCompose(v -> {
                                // Give item to buyer
                                return databaseManager.addCollectableItem(
                                        order.buyerUuid(),
                                        itemStack.getItem().getKey().toString(),
                                        serializeUpgrades(itemStack),
                                        serializeItemStack(itemStack),
                                        quantity
                                );
                            })
                            .thenCompose(v -> {
                                // Update or remove buy order
                                if (quantity >= order.quantity()) {
                                    return databaseManager.cancelBuyOrder(buyOrderId);
                                } else {
                                    return databaseManager.updateBuyOrderQuantity(buyOrderId, order.quantity() - quantity);
                                }
                            })
                            .thenApply(v -> new OrderResult(true, "Item sold successfully", buyOrderId));
                });
    }

    private CompletableFuture<OrderResult> fulfillSellOrder(long sellOrderId, UUID buyerUuid, int quantity, int pricePerUnit) {
        return databaseManager.getSellOrderById(sellOrderId)
                .thenCompose(orderOpt -> {
                    if (orderOpt.isEmpty()) {
                        return CompletableFuture.completedFuture(
                                new OrderResult(false, "Sell order not found", -1L)
                        );
                    }
                    SellOrder order = orderOpt.get();
                    int actualQuantity = Math.min(quantity, order.quantity());
                    long totalPrice = (long) actualQuantity * pricePerUnit;

                    // Give item to buyer
                    return databaseManager.addCollectableItem(
                            buyerUuid,
                            order.itemId(),
                            order.upgrades(),
                            order.itemData(),
                            actualQuantity
                    ).thenCompose(v -> {
                        // Give money to seller
                        return databaseManager.addCollectableMoney(order.sellerUuid(), totalPrice);
                    }).thenCompose(v -> {
                        // Update or remove sell order
                        if (actualQuantity >= order.quantity()) {
                            return databaseManager.cancelSellOrder(sellOrderId);
                        } else {
                            return databaseManager.updateSellOrderQuantity(sellOrderId, order.quantity() - actualQuantity);
                        }
                    }).thenApply(v -> new OrderResult(true, "Item purchased successfully", sellOrderId));
                });
    }

    private String serializeUpgrades(HItemStack itemStack) {
        return itemStack.getUpgrades().stream()
                .map(upgrade -> upgrade.getUpgrade().getId())
                .sorted()
                .reduce((a, b) -> a + "," + b)
                .orElse("");
    }

    private byte[] serializeItemStack(HItemStack itemStack) {
        return itemStack.getBukkitStack().serializeAsBytes();
    }

    public record OrderResult(boolean success, String message, long orderId) {}
}

