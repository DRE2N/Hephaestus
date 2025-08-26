package de.erethon.hephaestus.jobs;

import de.erethon.hecate.Hecate;
import de.erethon.hecate.data.DatabaseManager;
import de.erethon.hecate.data.HCharacter;
import de.erethon.hecate.progression.LevelUtil;
import de.erethon.hephaestus.Hephaestus;
import org.bukkit.entity.Player;

import java.util.concurrent.CompletableFuture;

public class JobCharacterBridgeUtil {

    private static final Hecate hecate = Hecate.getInstance();
    private static final DatabaseManager databaseManager = hecate.getDatabaseManager();

    public static CompletableFuture<HJob> getCharacterJob(HCharacter character) {
        if (character == null) {
            return CompletableFuture.completedFuture(null);
        }

        JobManager jobManager = getJobManager();
        if (jobManager == null) {
            return CompletableFuture.completedFuture(null);
        }

        return jobManager.getCharacterJob(character.getCharacterID());
    }

    public static HCharacter getCharacter(Player player) {
        return databaseManager.getCurrentCharacter(player);
    }

    public static CompletableFuture<Integer> getJobLevel(CharacterJob characterJob) {
        if (characterJob == null) {
            return CompletableFuture.completedFuture(-1);
        }
        return LevelUtil.getJobLevel((characterJob.character()));
    }

    public static void grantJobExperience(CharacterJob characterJob, long amount) {
        if (characterJob == null) {
            return;
        }
        LevelUtil.giveJobXp(characterJob.character(), amount);
    }

    public static CompletableFuture<Boolean> setCharacterJob(Player player, String jobId) {
        HCharacter character = getCharacter(player);
        if (character == null) {
            return CompletableFuture.completedFuture(false);
        }

        JobManager jobManager = getJobManager();
        if (jobManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        return jobManager.setCharacterJob(character.getCharacterID(), jobId);
    }

    public static CompletableFuture<Boolean> removeCharacterJob(Player player) {
        HCharacter character = getCharacter(player);
        if (character == null) {
            return CompletableFuture.completedFuture(false);
        }

        JobManager jobManager = getJobManager();
        if (jobManager == null) {
            return CompletableFuture.completedFuture(false);
        }

        return jobManager.removeCharacterJob(character.getCharacterID());
    }

    public static CompletableFuture<CharacterJob> getCharacterJobRecord(Player player) {
        HCharacter character = getCharacter(player);
        if (character == null) {
            return CompletableFuture.completedFuture(null);
        }

        return getCharacterJob(character).thenApply(job -> {
            if (job == null) {
                return null;
            }
            return new CharacterJob(character, job);
        });
    }

    private static JobManager getJobManager() {
        return Hephaestus.INSTANCE.getJobManager();
    }
}
