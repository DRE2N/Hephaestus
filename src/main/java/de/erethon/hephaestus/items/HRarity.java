package de.erethon.hephaestus.items;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum HRarity {
    TRASH(NamedTextColor.DARK_GRAY),
    COMMON(NamedTextColor.WHITE),
    UNCOMMON(NamedTextColor.GREEN),
    RARE(NamedTextColor.BLUE),
    EPIC(NamedTextColor.DARK_PURPLE),
    LEGENDARY(NamedTextColor.GOLD),
    MYTHIC(NamedTextColor.DARK_RED);

    private final TextColor color;

    HRarity(TextColor color) {
        this.color = color;
    }

    public TextColor getColor() {
        return color;
    }
}
