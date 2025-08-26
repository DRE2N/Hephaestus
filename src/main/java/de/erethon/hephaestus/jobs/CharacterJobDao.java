package de.erethon.hephaestus.jobs;

import org.jdbi.v3.sqlobject.customizer.Bind;
import org.jdbi.v3.sqlobject.statement.SqlQuery;
import org.jdbi.v3.sqlobject.statement.SqlUpdate;

import java.util.Optional;
import java.util.UUID;

public interface CharacterJobDao {

    @SqlUpdate("""
        CREATE TABLE IF NOT EXISTS character_jobs (
            character_uuid UUID PRIMARY KEY,
            job_id VARCHAR(255) NOT NULL,
            selected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    void createTable();

    @SqlUpdate("""
        INSERT INTO character_jobs (character_uuid, job_id, updated_at) 
        VALUES (:characterUuid, :jobId, CURRENT_TIMESTAMP)
        ON CONFLICT (character_uuid) 
        DO UPDATE SET job_id = :jobId, updated_at = CURRENT_TIMESTAMP
        """)
    void setCharacterJob(@Bind("characterUuid") UUID characterUuid, @Bind("jobId") String jobId);

    @SqlQuery("SELECT job_id FROM character_jobs WHERE character_uuid = :characterUuid")
    Optional<String> getCharacterJobId(@Bind("characterUuid") UUID characterUuid);

    @SqlUpdate("DELETE FROM character_jobs WHERE character_uuid = :characterUuid")
    void removeCharacterJob(@Bind("characterUuid") UUID characterUuid);

    @SqlQuery("SELECT COUNT(*) FROM character_jobs WHERE job_id = :jobId")
    int getJobPlayerCount(@Bind("jobId") String jobId);
}
