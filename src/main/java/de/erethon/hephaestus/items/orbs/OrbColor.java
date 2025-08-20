package de.erethon.hephaestus.items.orbs;

import net.kyori.adventure.text.format.NamedTextColor;

public enum OrbColor {
    RED(NamedTextColor.RED, "Red"),
    BLUE(NamedTextColor.BLUE, "Blue"),
    GREEN(NamedTextColor.GREEN, "Green"),
    PRISMATIC(NamedTextColor.LIGHT_PURPLE, "Prismatic");

    private final NamedTextColor textColor;
    private final String displayName;

    OrbColor(NamedTextColor textColor, String displayName) {
        this.textColor = textColor;
        this.displayName = displayName;
    }

    public NamedTextColor getTextColor() {
        return textColor;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static OrbColor fromString(String s) {
        if (s == null) return null;
        try {
            return OrbColor.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}

