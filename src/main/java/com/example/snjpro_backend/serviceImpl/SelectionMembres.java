package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.MembreCompetence;
import com.example.snjpro_backend.enumeration.Enumerations.CibleNotification;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.repository.MembreRepository;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.Period;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Sélection des membres destinataires d'une communication ou d'une mobilisation (F24, US08, US11).
 */
@Component
@RequiredArgsConstructor
public class SelectionMembres {
    private final MembreRepository membreRepository;

    /** Critères de sélection d'une mobilisation. Tous facultatifs : null = pas de filtre. */
    @Builder
    public record Criteres(List<String> regions, List<String> competences, boolean toutesCompetences,
                           List<Long> cohorteIds, List<StatutMembreEnum> statuts, SexeEnum sexe,
                           Integer ageMin, Integer ageMax, boolean telephoneVerifieUniquement,
                           Set<Long> exclure) { }

    /** Destinataires d'une notification selon sa cible. */
    public List<Membre> parCible(CibleNotification cible, List<String> valeurs) {
        List<String> v = valeurs == null ? List.of() : valeurs.stream().filter(StringUtils::hasText).map(String::trim).toList();
        return switch (cible) {
            case TOUS -> membreRepository.findAllNonArchives();
            case REGION -> filtrer(Criteres.builder().regions(v).build());
            case COHORTE -> filtrer(Criteres.builder().cohorteIds(v.stream().map(Long::valueOf).toList()).build());
            case SELECTION -> membreRepository.findAllById(v.stream().map(Long::valueOf).toList()).stream()
                    .filter(m -> m.getStatut() != StatutMembreEnum.ARCHIVE).toList();
        };
    }

    public List<Membre> filtrer(Criteres c) {
        Set<String> regions = normaliser(c.regions());
        Set<String> competences = normaliser(c.competences());
        LocalDate aujourdhui = LocalDate.now();
        return membreRepository.findAllNonArchives().stream()
                .filter(m -> regions.isEmpty() || (m.getLocalisation() != null && m.getLocalisation().getRegion() != null
                        && regions.contains(m.getLocalisation().getRegion().trim().toLowerCase())))
                .filter(m -> c.cohorteIds() == null || c.cohorteIds().isEmpty() || c.cohorteIds().contains(m.getCohorte().getId()))
                .filter(m -> c.statuts() == null || c.statuts().isEmpty() || c.statuts().contains(m.getStatut()))
                .filter(m -> c.sexe() == null || c.sexe() == m.getSexe())
                .filter(m -> c.ageMin() == null || (m.getDateNaissance() != null && Period.between(m.getDateNaissance(), aujourdhui).getYears() >= c.ageMin()))
                .filter(m -> c.ageMax() == null || (m.getDateNaissance() != null && Period.between(m.getDateNaissance(), aujourdhui).getYears() <= c.ageMax()))
                .filter(m -> !c.telephoneVerifieUniquement() || Boolean.TRUE.equals(m.getTelephoneVerifie()))
                .filter(m -> !Boolean.FALSE.equals(m.getDisponibleMobilisation()))
                .filter(m -> c.exclure() == null || !c.exclure().contains(m.getId()))
                .filter(m -> competences.isEmpty() || correspond(m, competences, c.toutesCompetences()))
                .toList();
    }

    /** Compétences du membre qui correspondent à la liste demandée. */
    public static List<String> competencesCorrespondantes(Membre m, Collection<String> demandees) {
        Set<String> d = normaliser(demandees == null ? null : new ArrayList<>(demandees));
        return m.getCompetences().stream().map(MembreCompetence::getIntitule)
                .filter(i -> d.contains(i.trim().toLowerCase())).toList();
    }

    private static boolean correspond(Membre m, Set<String> competences, boolean toutes) {
        Set<String> possedees = m.getCompetences().stream().map(x -> x.getIntitule().trim().toLowerCase()).collect(Collectors.toSet());
        return toutes ? possedees.containsAll(competences) : competences.stream().anyMatch(possedees::contains);
    }

    private static Set<String> normaliser(List<String> valeurs) {
        if (valeurs == null) return Set.of();
        return valeurs.stream().filter(StringUtils::hasText).map(s -> s.trim().toLowerCase()).collect(Collectors.toSet());
    }
}
