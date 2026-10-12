package com.example.snjpro_backend.specification;

import com.example.snjpro_backend.dto.MembreFilterDTO;
import com.example.snjpro_backend.entity.Localisation;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.MembreCompetence;
import com.example.snjpro_backend.entity.SituationProfessionnelle;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * Construit dynamiquement les critères de recherche avancée des membres (F13 / F14).
 * Tous les critères renseignés sont combinés avec un ET logique.
 */
public final class MembreSpecification {

    private MembreSpecification() {
    }

    public static Specification<Membre> avecFiltres(MembreFilterDTO filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (StringUtils.hasText(filter.getQuery())) {
                String like = contient(filter.getQuery());
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("nom")), like),
                        cb.like(cb.lower(root.get("prenom")), like),
                        cb.like(cb.lower(root.get("identifiantSnj")), like),
                        cb.like(cb.lower(cb.concat(cb.concat(root.get("prenom"), " "), root.get("nom"))), like)
                ));
            }
            if (filter.getCohorteId() != null) {
                predicates.add(cb.equal(root.get("cohorte").get("id"), filter.getCohorteId()));
            }
            if (filter.getStatut() != null) {
                predicates.add(cb.equal(root.get("statut"), filter.getStatut()));
            }
            if (filter.getSexe() != null) {
                predicates.add(cb.equal(root.get("sexe"), filter.getSexe()));
            }
            if (StringUtils.hasText(filter.getRegion())) {
                Join<Membre, Localisation> loc = root.join("localisation", JoinType.LEFT);
                predicates.add(cb.equal(cb.lower(loc.get("region")), filter.getRegion().trim().toLowerCase()));
            }
            if (filter.getStatutProfessionnel() != null
                    || StringUtils.hasText(filter.getProfession())
                    || StringUtils.hasText(filter.getSecteurActivite())) {
                Join<Membre, SituationProfessionnelle> sp = root.join("situationProfessionnelle", JoinType.LEFT);
                if (filter.getStatutProfessionnel() != null) {
                    predicates.add(cb.equal(sp.get("statut"), filter.getStatutProfessionnel()));
                }
                if (StringUtils.hasText(filter.getProfession())) {
                    predicates.add(cb.like(cb.lower(sp.get("profession")), contient(filter.getProfession())));
                }
                if (StringUtils.hasText(filter.getSecteurActivite())) {
                    predicates.add(cb.like(cb.lower(sp.get("secteurActivite")), contient(filter.getSecteurActivite())));
                }
            }
            if (StringUtils.hasText(filter.getCompetence())) {
                // Sous-requête EXISTS : évite les doublons de lignes quand un membre a plusieurs compétences
                Subquery<Long> sub = query.subquery(Long.class);
                Root<MembreCompetence> mc = sub.from(MembreCompetence.class);
                sub.select(mc.get("id")).where(
                        cb.equal(mc.get("membre"), root),
                        cb.like(cb.lower(mc.get("intitule")), contient(filter.getCompetence()))
                );
                predicates.add(cb.exists(sub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String contient(String valeur) {
        return "%" + valeur.trim().toLowerCase() + "%";
    }
}
