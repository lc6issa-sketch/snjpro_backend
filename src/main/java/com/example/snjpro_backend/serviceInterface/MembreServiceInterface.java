package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MembreServiceInterface {
    MembreResponseDTO ajouterMembre(MembreDTO membreDTO, String createdBy, boolean forcer);
    MembreResponseDTO modifierMembre(Long id, MembreDTO membreDTO);
    MembreResponseDTO obtenirMembre(Long id);
    MembreResponseDTO obtenirParIdentifiantSnj(String identifiantSnj);
    Page<MembreResponseDTO> obtenirTousLesMembres(Pageable pageable);
    Page<MembreResponseDTO> rechercherMembres(String query, Pageable pageable);
    Page<MembreResponseDTO> obtenirMembresParCohorte(Long cohorteId, Pageable pageable);
    MembreResponseDTO archiverMembre(Long id);
    MembreResponseDTO changerStatut(Long id, StatutMembreEnum statut);
    void supprimerMembre(Long id);
    String genererIdentifiantSnj(Long cohorteId);
    ProfilMembreDTO obtenirProfilComplet(Long id);
    MembreResponseDTO creerCompte(Long membreId, CompteMembreDTO dto);
    MembreResponseDTO mettreAJourProfil(Long membreId, MiseAJourProfilDTO dto);
    /** Réapplique des valeurs (restauration depuis l'historique) : champ → valeur. */
    MembreResponseDTO restaurer(Long membreId, java.util.Map<String, String> valeurs, Long historiqueId);
    /** Doublons potentiels avant enregistrement (nouveau membre, étape « Contact »). */
    java.util.List<String> verifierDoublons(String prenom, String nom, java.time.LocalDate dateNaissance, String telephone, String email, Long exclureId);
    String identifiantSuivant(Long cohorteId);
    MembreResponseDTO changerDisponibilite(Long membreId, boolean disponible);
    MembreResponseDTO changerPhoto(Long membreId, org.springframework.web.multipart.MultipartFile photo);
    com.example.snjpro_backend.dto.DocumentDTOs.Fichier photo(Long membreId);
}
