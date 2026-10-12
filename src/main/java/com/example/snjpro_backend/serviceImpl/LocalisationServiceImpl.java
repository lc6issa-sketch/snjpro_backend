package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.LocalisationDTO;
import com.example.snjpro_backend.entity.Localisation;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.exception.ConflitException;
import com.example.snjpro_backend.mapper.MembreMapper;
import com.example.snjpro_backend.repository.LocalisationRepository;
import com.example.snjpro_backend.repository.MembreRepository;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.example.snjpro_backend.serviceInterface.LocalisationServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class LocalisationServiceImpl implements LocalisationServiceInterface {
    private final LocalisationRepository localisationRepository;
    private final MembreRepository membreRepository;
    private final MembreMapper mapper;
    private final HistoriqueServiceInterface historiqueService;

    @Override
    public LocalisationDTO enregistrer(Long membreId, LocalisationDTO dto) {
        if (localisationRepository.findByMembreId(membreId).isPresent())
            throw new ConflitException("Une localisation existe déjà pour ce membre. Utilisez PUT pour la modifier.");
        return creer(membreId, dto);
    }

    /** Met à jour la localisation, ou la crée si elle n'existe pas encore. */
    @Override
    public LocalisationDTO modifier(Long membreId, LocalisationDTO dto) {
        Localisation localisation = localisationRepository.findByMembreId(membreId).orElse(null);
        if (localisation == null) return creer(membreId, dto);
        appliquer(localisation, dto);
        historiqueService.enregistrer("MODIFICATION_LOCALISATION", "MEMBRE", membreId, dto.getRegion());
        return mapper.toLocalisationDTO(localisationRepository.save(localisation));
    }

    @Override @Transactional(readOnly = true)
    public LocalisationDTO obtenir(Long membreId) {
        return localisationRepository.findByMembreId(membreId)
                .map(mapper::toLocalisationDTO)
                .orElseThrow(() -> new EntityNotFoundException("Localisation non trouvée pour le membre : " + membreId));
    }

    @Override @Transactional(readOnly = true)
    public List<String> obtenirToutesLesRegions() {
        return localisationRepository.findAllDistinctRegions();
    }

    private LocalisationDTO creer(Long membreId, LocalisationDTO dto) {
        Membre membre = membreRepository.findById(membreId)
                .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + membreId));
        Localisation localisation = Localisation.builder().membre(membre).build();
        appliquer(localisation, dto);
        localisation = localisationRepository.save(localisation);
        membre.setLocalisation(localisation);
        historiqueService.enregistrer("AJOUT_LOCALISATION", "MEMBRE", membreId, dto.getRegion());
        return mapper.toLocalisationDTO(localisation);
    }

    private void appliquer(Localisation l, LocalisationDTO dto) {
        l.setPays(StringUtils.hasText(dto.getPays()) ? dto.getPays().trim() : "Mali");
        l.setRegion(StringUtils.hasText(dto.getRegion()) ? dto.getRegion().trim() : null);
        l.setCercle(dto.getCercle());
        l.setCommune(dto.getCommune());
        l.setQuartier(dto.getQuartier());
        l.setAdresseComplete(dto.getAdresseComplete());
    }
}
