package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.SituationProfessionnelleDTO;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.SituationProfessionnelle;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.mapper.MembreMapper;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.repository.SituationProfessionnelleRepository;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.SituationProfessionnelleServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class SituationProfessionnelleServiceImpl implements SituationProfessionnelleServiceInterface {
    private final SituationProfessionnelleRepository situationRepository;
    private final MembreRepository membreRepository;
    private final MembreMapper mapper;
    private final HistoriqueServiceInterface historiqueService;

    @Override
    public SituationProfessionnelleDTO enregistrer(Long membreId, SituationProfessionnelleDTO dto) {
        if (situationRepository.findByMembreId(membreId).isPresent())
            throw new ConflitException("Une situation professionnelle existe déjà pour ce membre. Utilisez PUT pour la modifier.");
        return creer(membreId, dto);
    }

    /** Met à jour la situation, ou la crée si elle n'existe pas encore. */
    @Override
    public SituationProfessionnelleDTO modifier(Long membreId, SituationProfessionnelleDTO dto) {
        SituationProfessionnelle sp = situationRepository.findByMembreId(membreId).orElse(null);
        if (sp == null) return creer(membreId, dto);
        java.util.List<java.util.Map<String, String>> champs = new java.util.ArrayList<>();
        changement(champs, "statutProfessionnel", "Situation professionnelle",
                sp.getStatut() == null ? null : sp.getStatut().name(), dto.getStatut() == null ? null : dto.getStatut().name());
        changement(champs, "profession", "Profession", sp.getProfession(), dto.getProfession());
        changement(champs, "employeur", "Employeur", sp.getNomEmployeur(), dto.getNomEmployeur());
        changement(champs, "secteurActivite", "Secteur d'activité", sp.getSecteurActivite(), dto.getSecteurActivite());
        appliquer(sp, dto);
        historiqueService.enregistrerModifications("MODIFICATION", "MEMBRE", membreId, "Situation professionnelle", champs);
        return mapper.toSituationDTO(situationRepository.save(sp));
    }

    @Override @Transactional(readOnly = true)
    public SituationProfessionnelleDTO obtenir(Long membreId) {
        return situationRepository.findByMembreId(membreId)
                .map(mapper::toSituationDTO)
                .orElseThrow(() -> new EntityNotFoundException("Situation professionnelle non trouvée pour le membre : " + membreId));
    }

    private SituationProfessionnelleDTO creer(Long membreId, SituationProfessionnelleDTO dto) {
        Membre membre = membreRepository.findById(membreId)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + membreId));
        SituationProfessionnelle sp = SituationProfessionnelle.builder().membre(membre).build();
        appliquer(sp, dto);
        sp = situationRepository.save(sp);
        membre.setSituationProfessionnelle(sp);
        historiqueService.enregistrer("AJOUT_SITUATION", "MEMBRE", membreId, dto.getStatut() + " / " + dto.getProfession());
        return mapper.toSituationDTO(sp);
    }

    private static void changement(java.util.List<java.util.Map<String, String>> l, String champ, String libelle, String avant, String apres) {
        String a = avant == null || avant.isBlank() ? null : avant.trim();
        String b = apres == null || apres.isBlank() ? null : apres.trim();
        if (java.util.Objects.equals(a, b)) return;
        java.util.Map<String, String> c = new java.util.LinkedHashMap<>();
        c.put("champ", champ);
        c.put("libelle", libelle);
        c.put("ancienne", avant);
        c.put("nouvelle", apres);
        l.add(c);
    }

    private void appliquer(SituationProfessionnelle sp, SituationProfessionnelleDTO dto) {
        sp.setStatut(dto.getStatut());
        sp.setProfession(dto.getProfession());
        sp.setSecteurActivite(dto.getSecteurActivite());
        sp.setNomEmployeur(dto.getNomEmployeur());
        sp.setDescription(dto.getDescription());
        sp.setDateDebutActivite(dto.getDateDebutActivite());
    }
}
