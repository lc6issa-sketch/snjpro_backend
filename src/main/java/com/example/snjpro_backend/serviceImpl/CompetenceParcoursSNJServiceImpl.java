package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.CompetenceDTO;
import com.example.snjpro_backend.dto.ParcoursSNJDTO;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.MembreCompetence;
import com.example.snjpro_backend.entity.ParcoursSNJ;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.mapper.MembreMapper;
import com.example.snjpro_backend.repository.MembreCompetenceRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.repository.ParcoursSNJRepository;
import com.example.snjpro_backend.serviceInterface.CompetenceParcoursSNJServiceInterface;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CompetenceParcoursSNJServiceImpl implements CompetenceParcoursSNJServiceInterface {
    private final MembreCompetenceRepository competenceRepository;
    private final ParcoursSNJRepository parcoursRepository;
    private final MembreRepository membreRepository;
    private final MembreMapper mapper;
    private final HistoriqueServiceInterface historiqueService;

    // ---------- Compétences ----------

    @Override
    public CompetenceDTO ajouterCompetence(Long membreId, CompetenceDTO dto) {
        Membre membre = trouverMembre(membreId);
        String intitule = dto.getIntitule().trim();
        boolean existe = competenceRepository.findByMembreId(membreId).stream()
                .anyMatch(c -> c.getIntitule().equalsIgnoreCase(intitule));
        if (existe)
            throw new ConflitException("Le membre possède déjà la compétence « " + intitule + " ».");
        MembreCompetence competence = MembreCompetence.builder()
                .membre(membre)
                .intitule(intitule)
                .categorie(dto.getCategorie())
                .niveau(dto.getNiveau())
                .description(dto.getDescription())
                .build();
        competence = competenceRepository.save(competence);
        historiqueService.enregistrer("AJOUT_COMPETENCE", "MEMBRE", membreId, intitule);
        return mapper.toCompetenceDTO(competence);
    }

    @Override @Transactional(readOnly = true)
    public List<CompetenceDTO> obtenirCompetencesMembre(Long membreId) {
        trouverMembre(membreId);
        return competenceRepository.findByMembreId(membreId).stream().map(mapper::toCompetenceDTO).toList();
    }

    @Override
    public CompetenceDTO modifierCompetence(Long membreId, Long id, CompetenceDTO dto) {
        MembreCompetence competence = trouverCompetence(membreId, id);
        competence.setIntitule(dto.getIntitule().trim());
        competence.setCategorie(dto.getCategorie());
        competence.setNiveau(dto.getNiveau());
        competence.setDescription(dto.getDescription());
        historiqueService.enregistrer("MODIFICATION_COMPETENCE", "MEMBRE", membreId, competence.getIntitule());
        return mapper.toCompetenceDTO(competenceRepository.save(competence));
    }

    @Override
    public void supprimerCompetence(Long membreId, Long id) {
        MembreCompetence competence = trouverCompetence(membreId, id);
        competence.getMembre().getCompetences().remove(competence);
        competenceRepository.delete(competence);
        historiqueService.enregistrer("SUPPRESSION_COMPETENCE", "MEMBRE", membreId, competence.getIntitule());
    }

    @Override @Transactional(readOnly = true)
    public List<String> obtenirIntitulesCompetences() {
        return competenceRepository.findDistinctIntitules();
    }

    // ---------- Parcours SNJ ----------

    @Override
    public ParcoursSNJDTO ajouterParcours(Long membreId, ParcoursSNJDTO dto) {
        Membre membre = trouverMembre(membreId);
        verifierDates(dto);
        ParcoursSNJ parcours = ParcoursSNJ.builder()
                .membre(membre)
                .intitule(dto.getIntitule().trim())
                .type(dto.getType())
                .description(dto.getDescription())
                .dateDebut(dto.getDateDebut())
                .dateFin(dto.getDateFin())
                .lieu(dto.getLieu())
                .build();
        parcours = parcoursRepository.save(parcours);
        historiqueService.enregistrer("AJOUT_PARCOURS", "MEMBRE", membreId, parcours.getIntitule());
        return mapper.toParcoursDTO(parcours);
    }

    @Override @Transactional(readOnly = true)
    public List<ParcoursSNJDTO> obtenirParcoursMembre(Long membreId) {
        trouverMembre(membreId);
        return parcoursRepository.findByMembreIdOrderByDateDebutDesc(membreId).stream()
                .map(mapper::toParcoursDTO).toList();
    }

    @Override
    public ParcoursSNJDTO modifierParcours(Long membreId, Long id, ParcoursSNJDTO dto) {
        ParcoursSNJ parcours = trouverParcours(membreId, id);
        verifierDates(dto);
        parcours.setIntitule(dto.getIntitule().trim());
        parcours.setType(dto.getType());
        parcours.setDescription(dto.getDescription());
        parcours.setDateDebut(dto.getDateDebut());
        parcours.setDateFin(dto.getDateFin());
        parcours.setLieu(dto.getLieu());
        historiqueService.enregistrer("MODIFICATION_PARCOURS", "MEMBRE", membreId, parcours.getIntitule());
        return mapper.toParcoursDTO(parcoursRepository.save(parcours));
    }

    @Override
    public void supprimerParcours(Long membreId, Long id) {
        ParcoursSNJ parcours = trouverParcours(membreId, id);
        parcours.getMembre().getParcours().remove(parcours);
        parcoursRepository.delete(parcours);
        historiqueService.enregistrer("SUPPRESSION_PARCOURS", "MEMBRE", membreId, parcours.getIntitule());
    }

    // ---------- Utilitaires ----------

    private Membre trouverMembre(Long membreId) {
        return membreRepository.findById(membreId)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + membreId));
    }

    private MembreCompetence trouverCompetence(Long membreId, Long id) {
        MembreCompetence competence = competenceRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Compétence non trouvée : " + id));
        if (!competence.getMembre().getId().equals(membreId))
            throw new EntityNotFoundException("Compétence " + id + " non trouvée pour le membre " + membreId);
        return competence;
    }

    private ParcoursSNJ trouverParcours(Long membreId, Long id) {
        ParcoursSNJ parcours = parcoursRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Parcours non trouvé : " + id));
        if (!parcours.getMembre().getId().equals(membreId))
            throw new EntityNotFoundException("Parcours " + id + " non trouvé pour le membre " + membreId);
        return parcours;
    }

    private void verifierDates(ParcoursSNJDTO dto) {
        if (dto.getDateDebut() != null && dto.getDateFin() != null && dto.getDateFin().isBefore(dto.getDateDebut()))
            throw new IllegalArgumentException("La date de fin doit être postérieure à la date de début.");
    }
}
