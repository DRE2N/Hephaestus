package de.erethon.hephaestus.commands;

import de.erethon.hephaestus.commands.SuggestionsRepository.BlockDataKeySuggestions;
import de.erethon.hephaestus.commands.SuggestionsRepository.UpgradeSuggestions;
import de.erethon.hephaestus.items.HItemLibrary;
import de.erethon.hephaestus.items.HItemStack;
import de.erethon.hephaestus.items.upgrades.HItemUpgrade;
import de.erethon.hephaestus.utils.HUpgradeResult;
import net.strokkur.commands.Aliases;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.arguments.StringArg;
import net.strokkur.commands.paper.Description;
import net.strokkur.commands.paper.Executor;
import net.strokkur.commands.permission.Permission;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import static net.strokkur.commands.arguments.StringArgType.GREEDY;

@Command("hephaestus")
@Description("Main Hephaestus command.")
@Aliases({"he", "hp", "h", "hh"})
class HephaestusCommand {
    private final HItemLibrary itemLibrary;

    public HephaestusCommand(HItemLibrary itemLibrary) {
        // This is not nice, but that's something that'll be improved by the library soon.
        SuggestionsRepository.ITEM_LIBRARY = itemLibrary;
        this.itemLibrary = itemLibrary;
    }

    @Executes("reload")
    @Permission("hephaestus.reload")
    void reload(CommandSender sender) {
        sender.sendRichMessage("<green>Reloading...");
        itemLibrary.reload();
        sender.sendRichMessage("<green>Reloaded Item Library.");
    }

    @Executes("setblockdata")
    @Permission("hephaestus.setblockdata")
    void setBlockData(@Executor Player player, @BlockDataKeySuggestions NamespacedKey key) {
        Block block = player.getTargetBlockExact(32);
        if (block == null) {
            player.sendRichMessage("<red>No block in sight.");
            return;
        }
        itemLibrary.runIfPresent(key, item -> item.setBlockData(block.getBlockData()));
    }

    @Executes("upgrade")
    @Permission("hephaestus.upgrade")
    void upgrade(@Executor Player player, @UpgradeSuggestions @StringArg(GREEDY) String id) {
        player.sendRichMessage("<gray><i>Upgrading Item in hand...");
        ItemStack stack = player.getInventory().getItemInMainHand();
        if (stack.getType() == Material.AIR) {
            player.sendRichMessage("<red>No item in hand.");
            return;
        }

        HItemUpgrade upgrade = itemLibrary.getUpgrade(id);
        if (upgrade == null) {
            player.sendRichMessage("<red>Upgrade not found.");
            return;
        }

        HUpgradeResult result = HItemStack.getFromStack(stack).rollAndAddUpgrade(id);
        player.sendRichMessage("<green>Result: <gray>" + result.toString());
    }

    @Executes("register")
    @Permission("hephaestus.register")
    void register(@Executor Player player, NamespacedKey key) {
        player.sendRichMessage("<gray><i>Parsing Item in hand...");
        if (player.getInventory().getItemInMainHand().getType() == Material.AIR) {
            player.sendRichMessage("<red>No item in hand.");
            return;
        }

        itemLibrary.register(player.getInventory().getItemInMainHand(), key);
        player.sendRichMessage("<green>Registered Item with key <gray>" + key + "<green>.");
        itemLibrary.save();
    }
}
