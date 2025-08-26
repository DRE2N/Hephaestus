package de.erethon.hephaestus.jobs;

import de.erethon.hecate.data.HCharacter;

/**
 * Record representing a character's job assignment
 * @param character the character data
 * @param job the assigned job
 */
public record CharacterJob(HCharacter character, HJob job) {
}
