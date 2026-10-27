package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.ApparenceDTO;
import com.example.snjpro_backend.entity.ParametreApplication;
import com.example.snjpro_backend.enumeration.Enumerations.LogoApplication;
import com.example.snjpro_backend.repository.ParametreApplicationRepository;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Logo de l'application et charte graphique qui en découle, choisis par l'administrateur pour tous les utilisateurs. */
@Service
@RequiredArgsConstructor
@Transactional
public class ApparenceService {
    private static final String CLE_LOGO = "LOGO";

    private final ParametreApplicationRepository repository;
    private final PerimetreService perimetre;
    private final HistoriqueServiceInterface historiqueService;

    @Transactional(readOnly = true)
    public ApparenceDTO apparence() {
        LogoApplication logo = repository.findById(CLE_LOGO)
                .map(p -> {
                    try { return LogoApplication.valueOf(p.getValeur()); }
                    catch (IllegalArgumentException | NullPointerException e) { return LogoApplication.VERT; }
                })
                .orElse(LogoApplication.VERT);
        return new ApparenceDTO(logo);
    }

    public ApparenceDTO changer(ApparenceDTO dto) {
        ParametreApplication p = repository.findById(CLE_LOGO).orElseGet(() -> ParametreApplication.builder().cle(CLE_LOGO).build());
        String avant = p.getValeur();
        p.setValeur(dto.getLogo().name());
        p.setModifiePar(perimetre.utilisateurCourant().getUsername());
        repository.save(p);
        if (!dto.getLogo().name().equals(avant))
            historiqueService.enregistrer("CHANGEMENT_LOGO", "APPLICATION", null,
                    "Logo : " + (avant == null ? LogoApplication.VERT.name() : avant) + " → " + dto.getLogo().name());
        return apparence();
    }
}
