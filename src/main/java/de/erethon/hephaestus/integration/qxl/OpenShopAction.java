package de.erethon.hephaestus.integration.qxl;

import de.erethon.hephaestus.Hephaestus;
import de.erethon.hephaestus.shops.Shop;
import de.erethon.hephaestus.shops.ShopManager;
import de.erethon.hephaestus.shops.gui.ShopGUI;
import de.erethon.questsxl.common.Quester;
import de.erethon.questsxl.common.doc.QLoadableDoc;
import de.erethon.questsxl.common.doc.QParamDoc;
import de.erethon.questsxl.common.script.QConfig;
import de.erethon.questsxl.component.action.QBaseAction;
import de.erethon.questsxl.player.QPlayer;
import org.bukkit.Bukkit;

@QLoadableDoc(
        value = "open_shop",
        description = "Opens a Hephaestus shop GUI for the player.",
        shortExample = "open_shop: shop=baker",
        longExample = {
                "open_shop:",
                "  shop: baker"
        }
)
public class OpenShopAction extends QBaseAction {

    @QParamDoc(name = "shop", description = "The Hephaestus shop ID to open.", required = true)
    private String shopId;

    @Override
    public void playInternal(Quester quester) {
        if (!conditions(quester)) {
            return;
        }
        execute(quester, this::openShop);
        onFinish(quester);
    }

    private void openShop(QPlayer qPlayer) {
        Hephaestus plugin = Hephaestus.INSTANCE;
        if (shopId == null || shopId.isBlank()) {
            plugin.getLogger().warning("QXL open_shop failed: missing shop id");
            return;
        }
        ShopManager shopManager = plugin.getShopManager();
        if (shopManager == null) {
            plugin.getLogger().warning("QXL open_shop failed: shop system is not initialized");
            return;
        }
        Shop shop = shopManager.getShop(shopId);
        if (shop == null) {
            plugin.getLogger().warning("QXL open_shop failed: unknown shop '" + shopId + "'");
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> new ShopGUI(plugin, qPlayer.getPlayer(), shop).open());
    }

    @Override
    public void load(QConfig cfg) {
        super.load(cfg);
        shopId = cfg.getString("shop");
    }
}
