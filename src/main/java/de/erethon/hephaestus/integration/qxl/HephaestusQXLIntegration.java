package de.erethon.hephaestus.integration.qxl;

import de.erethon.questsxl.QuestsXL;
import de.erethon.questsxl.common.QRegistries;

public final class HephaestusQXLIntegration {

    private HephaestusQXLIntegration() {
    }

    public static void register() {
        QuestsXL qxl = QuestsXL.get();
        qxl.registerComponent(QRegistries.ACTIONS, "open_shop", OpenShopAction::new);
    }
}
