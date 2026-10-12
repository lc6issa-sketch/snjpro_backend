package com.example.snjpro_backend.exception;

/**
 * Levée lorsqu'une opération entre en conflit avec l'état des données
 * (doublon, ressource déjà existante...). Renvoie un HTTP 409.
 */
public class ConflitException extends RuntimeException {
    public ConflitException(String message) {
        super(message);
    }
}
