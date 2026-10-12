package com.example.snjpro_backend.exception;

/**
 * Levée lorsque trop de tentatives de connexion échouées ont été faites. Renvoie un HTTP 429.
 */
public class TropDeTentativesException extends RuntimeException {
    public TropDeTentativesException(String message) {
        super(message);
    }
}
