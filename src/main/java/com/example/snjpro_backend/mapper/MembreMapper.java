package com.example.snjpro_backend.mapper;

import com.example.snjpro_backend.dto.*;
import com.example.snjpro_backend.entity.*;
import org.springframework.stereotype.Component;

/**
 * Conversion des entités liées au membre vers leurs DTO.
 */
@Component
public class MembreMapper {

    public MembreResponseDTO toResponseDTO(Membre m) {
        MembreResponseDTO dto = new MembreResponseDTO();
        dto.setId(m.getId());
        dto.setIdentifiantSnj(m.getIdentifiantSnj());
        dto.setPrenom(m.getPrenom());
        dto.setNom(m.getNom());
        dto.setSexe(m.getSexe());
        dto.setDateNaissance(m.getDateNaissance());
        dto.setLieuNaissance(m.getLieuNaissance());
        dto.setNumeroTelephone(m.getNumeroTelephone());
        dto.setEmail(m.getEmail());
        dto.setPhotoUrl(m.getPhotoUrl());
        dto.setTelephoneSecondaire(m.getTelephoneSecondaire());
        dto.setTelephoneVerifie(Boolean.TRUE.equals(m.getTelephoneVerifie()));
        dto.setPersonneAPrevenir(m.getPersonneAPrevenir());
        dto.setPersonneAPrevenirTelephone(m.getPersonneAPrevenirTelephone());
        dto.setNationalite(m.getNationalite());
        dto.setGroupeSanguin(m.getGroupeSanguin());
        dto.setTaille(m.getTaille());
        dto.setPoids(m.getPoids());
        dto.setDernierDiplome(m.getDernierDiplome());
        dto.setDiplomes(diplomes(m));
        dto.setCategorie(m.getCategorie());
        dto.setCompagnie(m.getCompagnie());
        dto.setSection(m.getSection());
        dto.setBrigade(m.getBrigade());
        dto.setDateIncorporation(m.getDateIncorporation());
        dto.setDisponibleMobilisation(!Boolean.FALSE.equals(m.getDisponibleMobilisation()));
        dto.setTauxCompletude(com.example.snjpro_backend.serviceImpl.CompletudeProfil.taux(m));
        dto.setStatut(m.getStatut());
        dto.setCohorteId(m.getCohorte().getId());
        dto.setCohorteNom(m.getCohorte().getNom());
        dto.setUserId(m.getUserId());
        dto.setDateCreation(m.getDateCreation());
        dto.setDateModification(m.getDateModification());
        if (m.getSituationProfessionnelle() != null) {
            dto.setSituationProfessionnelle(toSituationDTO(m.getSituationProfessionnelle()));
        }
        if (m.getLocalisation() != null) {
            dto.setLocalisation(toLocalisationDTO(m.getLocalisation()));
        }
        return dto;
    }

    /** Diplômes du membre ; un dernier diplôme saisi ailleurs (ancienne fiche, demande) y est ajouté s'il manque. */
    private static java.util.List<String> diplomes(Membre m) {
        java.util.List<String> l = new java.util.ArrayList<>(m.getDiplomes() == null ? java.util.List.of() : m.getDiplomes());
        String dernier = m.getDernierDiplome();
        if (dernier != null && l.stream().noneMatch(d -> d.equalsIgnoreCase(dernier))) l.add(dernier);
        return l;
    }

    public SituationProfessionnelleDTO toSituationDTO(SituationProfessionnelle sp) {
        SituationProfessionnelleDTO dto = new SituationProfessionnelleDTO();
        dto.setId(sp.getId());
        dto.setMembreId(sp.getMembre().getId());
        dto.setStatut(sp.getStatut());
        dto.setProfession(sp.getProfession());
        dto.setSecteurActivite(sp.getSecteurActivite());
        dto.setNomEmployeur(sp.getNomEmployeur());
        dto.setDescription(sp.getDescription());
        dto.setDateDebutActivite(sp.getDateDebutActivite());
        return dto;
    }

    public LocalisationDTO toLocalisationDTO(Localisation l) {
        LocalisationDTO dto = new LocalisationDTO();
        dto.setId(l.getId());
        dto.setMembreId(l.getMembre().getId());
        dto.setPays(l.getPays());
        dto.setRegion(l.getRegion());
        dto.setCercle(l.getCercle());
        dto.setCommune(l.getCommune());
        dto.setQuartier(l.getQuartier());
        dto.setAdresseComplete(l.getAdresseComplete());
        return dto;
    }

    public CompetenceDTO toCompetenceDTO(MembreCompetence c) {
        CompetenceDTO dto = new CompetenceDTO();
        dto.setId(c.getId());
        dto.setMembreId(c.getMembre().getId());
        dto.setIntitule(c.getIntitule());
        dto.setCategorie(c.getCategorie());
        dto.setNiveau(c.getNiveau());
        dto.setDescription(c.getDescription());
        return dto;
    }

    public ParcoursSNJDTO toParcoursDTO(ParcoursSNJ p) {
        ParcoursSNJDTO dto = new ParcoursSNJDTO();
        dto.setId(p.getId());
        dto.setMembreId(p.getMembre().getId());
        dto.setIntitule(p.getIntitule());
        dto.setType(p.getType());
        dto.setDescription(p.getDescription());
        dto.setDateDebut(p.getDateDebut());
        dto.setDateFin(p.getDateFin());
        dto.setLieu(p.getLieu());
        return dto;
    }
}
