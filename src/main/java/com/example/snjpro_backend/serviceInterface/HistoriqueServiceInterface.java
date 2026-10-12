package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.entity.Historique;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public interface HistoriqueServiceInterface {
    void enregistrer(String action, String entite, Long entiteId, String details);
    void enregistrer(String action, String entite, Long entiteId, String details, String utilisateur);
    /** Trace une modification avec le détail des champs : [{champ, libelle, ancienne, nouvelle}]. */
    void enregistrerModifications(String action, String entite, Long entiteId, String details, List<Map<String, String>> champs);
    Page<Historique> lister(String entite, Long entiteId, String utilisateur, Pageable pageable);
    Page<Historique> rechercher(String utilisateur, String action, String entite, LocalDate du, LocalDate au,
                                String recherche, Pageable pageable);
    Historique obtenir(Long id);
    List<Map<String, String>> champsModifies(Historique h);
}
