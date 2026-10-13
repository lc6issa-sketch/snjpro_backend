package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.StatistiqueDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;

import java.util.List;

public interface StatistiqueServiceInterface {
    // Tableau de bord & statistiques (F19, F20, US09, US12)
    TableauDeBord tableauDeBord();
    Statistiques statistiques(Filtres filtres);
    byte[] exporterStatistiques(Filtres filtres, FormatFichier format);

    // Rapports (F35)
    RapportDTO genererRapport(DemandeRapport demande);
    List<RapportDTO> rapports();
    Fichier telechargerRapport(Long id);
    void supprimerRapport(Long id);

    // Export des données (F21)
    EstimationExport estimerExport(Export export);
    Fichier exporterMembres(Export export);
}
