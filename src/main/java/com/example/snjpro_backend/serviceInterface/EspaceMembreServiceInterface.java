package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.EspaceMembreDTOs.*;

public interface EspaceMembreServiceInterface {
    Accueil accueil();
    Completude completude(Long membreId);
    Carte carte(Long membreId);
    VerificationCarte verifierCarte(String qrContenu);
    /** Profil d'un membre dont on a scanné la carte (informations non sensibles). */
    ProfilScanne profilScanne(String qrContenu);
    com.example.snjpro_backend.dto.DocumentDTOs.Fichier photoScannee(String identifiantSnj, String signature);
    Cv cv(Long membreId);
    byte[] cvPdf(Long membreId);
}
