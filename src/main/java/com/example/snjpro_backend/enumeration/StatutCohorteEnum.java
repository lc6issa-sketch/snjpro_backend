package com.example.snjpro_backend.enumeration;

/**
 * Cycle de vie d'une cohorte (Si-SNJ) : EN_FORMATION → INCORPOREE → EN_FONCTION.
 * ARCHIVEE n'est pas un statut proposé à la saisie : il est posé uniquement par l'action « Archiver ».
 * Les anciennes valeurs (EN_RESERVE, LIBEREE, ACTIVE, TERMINEE) sont converties en EN_FONCTION au démarrage
 * par {@link com.example.snjpro_backend.config.MigrationSchema}.
 */
public enum StatutCohorteEnum {
    EN_FORMATION, INCORPOREE, EN_FONCTION, ARCHIVEE
}
