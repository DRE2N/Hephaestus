package de.erethon.hephaestus.jobs;

import de.erethon.bedrock.database.BedrockDBConnection;
import de.erethon.bedrock.database.EDatabaseManager;
import de.erethon.hephaestus.jobs.crafting.CraftingProgressDao;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

public class JobDatabaseManager extends EDatabaseManager {

    private CharacterJobDao characterJobDao;

    public JobDatabaseManager(BedrockDBConnection connection) {
        super(connection, new ThreadPoolExecutor(2, 4, 60L, java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>()));
    }

    @Override
    protected CompletableFuture<Void> initializeSchema() {
        return CompletableFuture.runAsync(() -> {
            try {
                jdbi.useHandle(handle -> {
                    CharacterJobDao dao = handle.attach(CharacterJobDao.class);
                    dao.createTable();
                    CraftingProgressDao craftingDao = handle.attach(CraftingProgressDao.class);
                    craftingDao.createDiscoveredRecipesTable();
                    craftingDao.createRecipeCraftsTable();
                });
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize job database schema", e);
            }
        }, asyncExecutor);
    }

    @Override
    protected void registerCustomMappers() {
    }

    public CompletableFuture<Void> setCharacterJob(UUID characterUuid, String jobId) {
        return executeAsync(handle -> {
            CharacterJobDao dao = handle.attach(CharacterJobDao.class);
            dao.setCharacterJob(characterUuid, jobId);
        });
    }

    public CompletableFuture<Optional<String>> getCharacterJobId(UUID characterUuid) {
        return queryAsync(handle -> {
            CharacterJobDao dao = handle.attach(CharacterJobDao.class);
            return dao.getCharacterJobId(characterUuid);
        });
    }

    public CompletableFuture<Void> removeCharacterJob(UUID characterUuid) {
        return executeAsync(handle -> {
            CharacterJobDao dao = handle.attach(CharacterJobDao.class);
            dao.removeCharacterJob(characterUuid);
        });
    }

    public CompletableFuture<Integer> getJobPlayerCount(String jobId) {
        return queryAsync(handle -> {
            CharacterJobDao dao = handle.attach(CharacterJobDao.class);
            return dao.getJobPlayerCount(jobId);
        });
    }
}
