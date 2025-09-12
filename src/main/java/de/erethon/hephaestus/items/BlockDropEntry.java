package de.erethon.hephaestus.items;

import java.util.Set;

public record BlockDropEntry(String itemId, double chance, int minAmount, int maxAmount, Set<String> requiredToolTags) {
}
