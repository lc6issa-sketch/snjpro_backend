package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.entity.Historique;
import com.example.snjpro_backend.repository.HistoriqueRepository;
import com.example.snjpro_backend.serviceInterface.HistoriqueServiceInterface;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.Predicate;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class HistoriqueServiceImpl implements HistoriqueServiceInterface {
    private final HistoriqueRepository historiqueRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void enregistrer(String action, String entite, Long entiteId, String details) {
        enregistrer(action, entite, entiteId, details, utilisateurCourant());
    }

    @Override
    public void enregistrer(String action, String entite, Long entiteId, String details, String utilisateur) {
        historiqueRepository.save(Historique.builder()
                .action(action)
                .entite(entite)
                .entiteId(entiteId)
                .details(details)
                .utilisateur(utilisateur)
                .adresseIp(adresseIp())
                .build());
    }

    @Override
    public void enregistrerModifications(String action, String entite, Long entiteId, String details, List<Map<String, String>> champs) {
        String json = null;
        if (champs != null && !champs.isEmpty()) {
            try {
                json = objectMapper.writeValueAsString(champs);
            } catch (JsonProcessingException e) {
                json = null;
            }
        }
        historiqueRepository.save(Historique.builder()
                .action(action).entite(entite).entiteId(entiteId)
                .details(details + (champs != null && !champs.isEmpty() ? " · " + champs.size() + " champ(s) modifié(s)" : ""))
                .utilisateur(utilisateurCourant()).adresseIp(adresseIp()).champsModifies(json)
                .build());
    }

    @Override @Transactional(readOnly = true)
    public Page<Historique> lister(String entite, Long entiteId, String utilisateur, Pageable pageable) {
        if (entite != null && entiteId != null)
            return historiqueRepository.findByEntiteAndEntiteId(entite.toUpperCase(), entiteId, pageable);
        if (utilisateur != null)
            return historiqueRepository.findByUtilisateur(utilisateur, pageable);
        return historiqueRepository.findAll(pageable);
    }

    @Override @Transactional(readOnly = true)
    public Page<Historique> rechercher(String utilisateur, String action, String entite, LocalDate du, LocalDate au,
                                       String recherche, Pageable pageable) {
        Specification<Historique> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (StringUtils.hasText(utilisateur)) p.add(cb.equal(root.get("utilisateur"), utilisateur.trim()));
            if (StringUtils.hasText(action)) p.add(cb.equal(root.get("action"), action.trim().toUpperCase()));
            if (StringUtils.hasText(entite)) p.add(cb.equal(root.get("entite"), entite.trim().toUpperCase()));
            if (du != null) p.add(cb.greaterThanOrEqualTo(root.get("dateAction"), du.atStartOfDay()));
            if (au != null) p.add(cb.lessThan(root.get("dateAction"), au.plusDays(1).atStartOfDay()));
            if (StringUtils.hasText(recherche)) {
                String q = "%" + recherche.trim().toLowerCase() + "%";
                p.add(cb.or(cb.like(cb.lower(root.get("details")), q), cb.like(cb.lower(root.get("utilisateur")), q),
                        cb.like(cb.lower(root.get("action")), q)));
            }
            query.orderBy(cb.desc(root.get("dateAction")));
            return cb.and(p.toArray(new Predicate[0]));
        };
        return historiqueRepository.findAll(spec, pageable);
    }

    @Override @Transactional(readOnly = true)
    public Historique obtenir(Long id) {
        return historiqueRepository.findById(id).orElseThrow(() -> new EntityNotFoundException("Opération non trouvée : " + id));
    }

    @Override
    public List<Map<String, String>> champsModifies(Historique h) {
        if (!StringUtils.hasText(h.getChampsModifies())) return List.of();
        try {
            return objectMapper.readValue(h.getChampsModifies(), new TypeReference<List<Map<String, String>>>() { });
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private String utilisateurCourant() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? auth.getName() : "system";
    }

    private static String adresseIp() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs)) return null;
        HttpServletRequest r = attrs.getRequest();
        String f = r.getHeader("X-Forwarded-For");
        return StringUtils.hasText(f) ? f.split(",")[0].trim() : r.getRemoteAddr();
    }
}
