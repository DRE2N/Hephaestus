package de.erethon.hephaestus.items.orbs;

import de.erethon.hephaestus.items.upgrades.HRolledUpgrade;

/**
 * Represents a single orb socket on an item. Holds a color and an optional inserted orb (rolled upgrade).
 */
public class OrbSocket {
    private final OrbColor color;
    private HRolledUpgrade inserted; // null if empty

    public OrbSocket(OrbColor color) {
        this.color = color;
    }

    public OrbColor getColor() {
        return color;
    }

    public HRolledUpgrade getInserted() {
        return inserted;
    }

    public void setInserted(HRolledUpgrade inserted) {
        this.inserted = inserted;
    }

    public boolean isEmpty() {
        return inserted == null;
    }
}
