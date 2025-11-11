package de.erethon.hephaestus.auctionhouse;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a buy order in the auction house.
 * A player offering to buy an item at a certain price.
 */
public record BuyOrder(
        long id,
        UUID buyerUuid,
        String itemId,
        String upgrades,
        int quantity,
        int pricePerUnit,
        Timestamp createdAt
) {
    public long getTotalPrice() {
        return (long) quantity * pricePerUnit;
    }
}

