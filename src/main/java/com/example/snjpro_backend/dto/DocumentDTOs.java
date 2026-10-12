package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.Enumerations.TypeDocument;
import com.example.snjpro_backend.enumeration.Enumerations.VisibiliteDocument;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

public final class DocumentDTOs {
    private DocumentDTOs() { }

    /** Fichier prêt à être renvoyé au client. */
    public record Fichier(byte[] contenu, String nom, String typeMime) { }

    @Data
    public static class DossierEdition {
        @NotBlank(message = "Le nom du dossier est obligatoire")
        @Size(max = 100)
        private String nom;
        private VisibiliteDocument visibiliteDefaut = VisibiliteDocument.MEMBRES;
    }

    @Data
    public static class DocumentEdition {
        @NotBlank
        @Size(max = 200)
        private String titre;
        private Long dossierId;
        private VisibiliteDocument visibilite;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DossierDTO {
        private Long id;
        private String nom;
        private VisibiliteDocument visibiliteDefaut;
        private long nombreDocuments;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DocumentDTO {
        private Long id;
        private String titre;
        private String nomFichier;
        private String extension;
        private String typeMime;
        private long taille;
        private Long dossierId;
        private String dossier;
        private VisibiliteDocument visibilite;
        private TypeDocument typeDocument;
        private boolean verifie;
        private boolean nouveau;
        private String ajoutePar;
        private LocalDateTime dateAjout;
        private String urlTelechargement;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Espace {
        private long utiliseOctets;
        private long quotaOctets;
        private int pourcentage;
    }

    /** Documents d'un membre et pièces obligatoires manquantes. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DocumentsMembre {
        private List<DocumentDTO> documents;
        private List<TypeDocument> piecesManquantes;
    }

    /** Vue « Mes documents » de l'application mobile. */
    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class MesDocuments {
        private List<DocumentDTO> personnels;
        private List<DocumentDTO> duSnj;
    }
}
