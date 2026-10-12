package com.example.snjpro_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultDTO {
    /** true : les données ont seulement été contrôlées, rien n'a été enregistré. */
    private boolean simulation;
    private int totalLignes;
    private int importesAvecSucces;
    private int lignesIgnorees;
    private int doublonsDetectes;
    /** Colonnes reconnues dans le fichier (après normalisation des en-têtes). */
    @Builder.Default
    private List<String> colonnesReconnues = new ArrayList<>();
    /** Colonnes du fichier qui n'ont pas été reconnues et ont donc été ignorées. */
    @Builder.Default
    private List<String> colonnesIgnorees = new ArrayList<>();
    @Builder.Default
    private List<String> identifiantsCrees = new ArrayList<>();
    @Builder.Default
    private List<ImportErreurDTO> erreurs = new ArrayList<>();
    private String message;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ImportErreurDTO {
        private int numeroLigne;
        private String champ;
        private String valeur;
        private String message;
    }
}
