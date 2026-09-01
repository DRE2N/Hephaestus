package de.erethon.hephaestus.commands;

import de.erethon.hephaestus.items.Grindstone;
import net.strokkur.commands.Aliases;
import net.strokkur.commands.Command;
import net.strokkur.commands.Executes;
import net.strokkur.commands.arguments.DoubleArg;
import net.strokkur.commands.arguments.IntArg;
import net.strokkur.commands.paper.Description;
import net.strokkur.commands.paper.Executor;
import net.strokkur.commands.permission.Permission;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Smoker;
import org.bukkit.entity.Player;

import java.util.OptionalDouble;
import java.util.OptionalInt;

@SuppressWarnings("OptionalUsedAsFieldOrParameterType")
@Command("grindstone")
@Description("Create a grindstone from a smoker block.")
@Aliases("gs")
class GrindstoneCommand {

    @Executes
    @Permission("hephaestus.grindstone")
    void execute(
        @Executor Player player,
        @DoubleArg(min = 0.0, max = 1.0) double successChance,
        @IntArg(min = 0) OptionalInt pricePerOrb,
        @DoubleArg(min = 0.0, max = 1.0) OptionalDouble fullRefundChance
    ) {
        Block targetBlock = player.getTargetBlockExact(5);
        if (targetBlock == null || targetBlock.getType() != Material.SMOKER) {
            player.sendRichMessage("<red>You must be looking at a smoker block within 5 blocks.");
            return;
        }

        Smoker smoker = (Smoker) targetBlock.getState();
        Grindstone grindstone = new Grindstone(smoker);
        grindstone.setSuccessChance((float) successChance);
        grindstone.setPricePerOrb(pricePerOrb.orElse(10));
        grindstone.setFullRefundChance((float) fullRefundChance.orElse(0.0));
        player.sendRichMessage("<green>Grindstone created with " + (successChance * 100) + "% shard chance, " + (fullRefundChance.orElse(0.0) * 100) + "% full refund chance, and " + pricePerOrb.orElse(10) + " coins per orb.");
    }
}
