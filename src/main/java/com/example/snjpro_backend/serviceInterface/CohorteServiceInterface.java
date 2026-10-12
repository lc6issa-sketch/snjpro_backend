package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.CohorteDTO;
import com.example.snjpro_backend.enumeration.StatutCohorteEnum;

import java.util.List;

public interface CohorteServiceInterface {
    CohorteDTO creerCohorte(CohorteDTO cohorteDTO);
    CohorteDTO modifierCohorte(Long id, CohorteDTO cohorteDTO);
    CohorteDTO obtenirCohorte(Long id);
    List<CohorteDTO> obtenirToutesLesCohortes(StatutCohorteEnum statut, String nom);
    CohorteDTO archiverCohorte(Long id);
    CohorteDTO reactiverCohorte(Long id);
    void supprimerCohorte(Long id);
    boolean existeParCode(String code);
    long compterMembresCohorte(Long id);
    CohorteDTO changerStatut(Long id, com.example.snjpro_backend.enumeration.StatutCohorteEnum statut, java.time.LocalDate dateEffet,
                             String motif, boolean notifierMembres);
    java.util.Map<String, Object> tableauDeBord(Long id);
    java.util.Map<String, Long> compteurs();
}
