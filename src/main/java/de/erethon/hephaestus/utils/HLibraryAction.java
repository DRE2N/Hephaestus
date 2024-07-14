package de.erethon.hephaestus.utils;

import de.erethon.hephaestus.items.HItem;

@FunctionalInterface
public interface HLibraryAction {
    void execute(HItem item);
}
