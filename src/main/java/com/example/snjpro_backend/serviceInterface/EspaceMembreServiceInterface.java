package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.EspaceMembreDTOs.*;

public interface EspaceMembreServiceInterface {
    Accueil accueil();
    Completude completude(Long membreId);
    Carte carte(Long membreId);
    VerificationCarte verifierCarte(String qrContenu);
    Cv cv(Long membreId);
    byte[] cvPdf(Long membreId);
}
