package de.erethon.hephaestus.utils;

public enum HUpgradeResult {
    TOO_LOW_LEVEL("hephaestus.upgrade.too_low_level"),
    TOO_MANY_UPGRADES("hephaestus.upgrade.too_many_upgrades"),
    INCOMPATIBLE_UPGRADE("hephaestus.upgrade.incompatible_upgrade"),
    TOO_BAD_RARITY("hephaestus.upgrade.too_bad_rarity"),
    MISSING_REQUIRED_UPGRADE("hephaestus.upgrade.missing_required_upgrade"),
    INVALID_ITEM("hephaestus.upgrade.invalid_item"),
    INVALID_UPGRADE("hephaestus.upgrade.invalid_upgrade"),
    NO_EMPTY_SOCKET("hephaestus.upgrade.no_empty_socket"),
    SOCKET_COLOR_MISMATCH("hephaestus.upgrade.socket_color_mismatch"),
    SUCCESS("hephaestus.upgrade.success");

    private final String translationKey;

    HUpgradeResult(String translationkey) {
        this.translationKey = translationkey;
    }

    public String translationKey() {
        return translationKey;
    }

}
