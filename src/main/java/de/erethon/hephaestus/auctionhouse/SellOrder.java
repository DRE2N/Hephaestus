package de.erethon.hephaestus.auctionhouse;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents a sell order in the auction house.
 * A player listing an item for sale.
 */
public record SellOrder(
        long id,
        UUID sellerUuid,
        String itemId,
        String upgrades,
        byte[] itemData,
        int quantity,
        int pricePerUnit,
        Timestamp createdAt
) {
    public long getTotalPrice() {
        return (long) quantity * pricePerUnit;
    }
}

