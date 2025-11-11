package de.erethon.hephaestus.auctionhouse;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Represents an item waiting to be collected by a player.
 * This could be from a sold item being returned (cancelled order) or a purchase.
 */
public record CollectableItem(
        long id,
        UUID playerUuid,
        String itemId,
        String upgrades,
        byte[] itemData,
        int quantity,
        Timestamp createdAt
) {}

