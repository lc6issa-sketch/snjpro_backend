package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.entity.Localisation;
import com.example.snjpro_backend.entity.Membre;
import com.example.snjpro_backend.entity.SituationProfessionnelle;
import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/**
 * Photographie des champs d'une fiche membre pour tracer les modifications champ par champ (F34)
 * et pouvoir restaurer les anciennes valeurs depuis l'historique.
 */
@Component
public class SuiviModifications {

    /** Séparateur des diplômes dans l'instantané texte. */
    private static final String SEPARATEUR_DIPLOMES = ", ";

    /** Libellés affichés dans l'historique, dans l'ordre. */
    public static final Map<String, String> LIBELLES = new LinkedHashMap<>();
    static {
        LIBELLES.put("prenom", "Prénom");
        LIBELLES.put("nom", "Nom");
        LIBELLES.put("sexe", "Sexe");
        LIBELLES.put("dateNaissance", "Date de naissance");
        LIBELLES.put("lieuNaissance", "Lieu de naissance");
        LIBELLES.put("numeroTelephone", "Téléphone");
        LIBELLES.put("telephoneVerifie", "Téléphone vérifié");
        LIBELLES.put("email", "E-mail");
        LIBELLES.put("personneAPrevenir", "Personne à prévenir");
        LIBELLES.put("personneAPrevenirTelephone", "Téléphone de la personne à prévenir");
        LIBELLES.put("statut", "Statut");
        LIBELLES.put("cohorte", "Cohorte");
        LIBELLES.put("nationalite", "Nationalité");
        LIBELLES.put("groupeSanguin", "Groupe sanguin");
        LIBELLES.put("diplomes", "Diplômes");
        LIBELLES.put("categorie", "Catégorie");
        LIBELLES.put("compagnie", "Compagnie");
        LIBELLES.put("section", "Section");
        LIBELLES.put("brigade", "Brigade");
        LIBELLES.put("statutProfessionnel", "Situation professionnelle");
        LIBELLES.put("profession", "Profession");
        LIBELLES.put("employeur", "Employeur");
        LIBELLES.put("secteurActivite", "Secteur d'activité");
        LIBELLES.put("region", "Région");
        LIBELLES.put("cercle", "Cercle");
        LIBELLES.put("commune", "Commune");
        LIBELLES.put("quartier", "Quartier");
    }

    public Map<String, String> instantane(Membre m) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("prenom", m.getPrenom());
        v.put("nom", m.getNom());
        v.put("sexe", texte(m.getSexe()));
        v.put("dateNaissance", texte(m.getDateNaissance()));
        v.put("lieuNaissance", m.getLieuNaissance());
        v.put("numeroTelephone", m.getNumeroTelephone());
        v.put("telephoneVerifie", Boolean.TRUE.equals(m.getTelephoneVerifie()) ? "Oui" : "Non");
        v.put("email", m.getEmail());
        v.put("personneAPrevenir", m.getPersonneAPrevenir());
        v.put("personneAPrevenirTelephone", m.getPersonneAPrevenirTelephone());
        v.put("statut", texte(m.getStatut()));
        v.put("cohorte", m.getCohorte() != null ? m.getCohorte().getNom() : null);
        v.put("nationalite", m.getNationalite());
        v.put("groupeSanguin", m.getGroupeSanguin());
        v.put("diplomes", m.getDiplomes() == null || m.getDiplomes().isEmpty() ? m.getDernierDiplome() : String.join(SEPARATEUR_DIPLOMES, m.getDiplomes()));
        v.put("categorie", m.getCategorie());
        v.put("compagnie", m.getCompagnie());
        v.put("section", m.getSection());
        v.put("brigade", m.getBrigade());
        SituationProfessionnelle sp = m.getSituationProfessionnelle();
        v.put("statutProfessionnel", sp == null ? null : texte(sp.getStatut()));
        v.put("profession", sp == null ? null : sp.getProfession());
        v.put("employeur", sp == null ? null : sp.getNomEmployeur());
        v.put("secteurActivite", sp == null ? null : sp.getSecteurActivite());
        Localisation l = m.getLocalisation();
        v.put("region", l == null ? null : l.getRegion());
        v.put("cercle", l == null ? null : l.getCercle());
        v.put("commune", l == null ? null : l.getCommune());
        v.put("quartier", l == null ? null : l.getQuartier());
        return v;
    }

    /** Champs dont la valeur a changé : [{champ, libelle, ancienne, nouvelle}]. */
    public List<Map<String, String>> differences(Map<String, String> avant, Map<String, String> apres) {
        List<Map<String, String>> l = new ArrayList<>();
        for (String champ : LIBELLES.keySet()) {
            String a = avant.get(champ), b = apres.get(champ);
            if (!Objects.equals(vide(a), vide(b))) {
                Map<String, String> c = new LinkedHashMap<>();
                c.put("champ", champ);
                c.put("libelle", LIBELLES.get(champ));
                c.put("ancienne", a);
                c.put("nouvelle", b);
                l.add(c);
            }
        }
        return l;
    }

    /** Réapplique une valeur (restauration). Les champs non restaurables (cohorte, téléphone vérifié) sont ignorés. */
    public boolean appliquer(Membre m, String champ, String valeur) {
        String v = vide(valeur);
        switch (champ) {
            case "prenom" -> { if (v != null) m.setPrenom(v); }
            case "nom" -> { if (v != null) m.setNom(v); }
            case "sexe" -> { if (v != null) m.setSexe(SexeEnum.valueOf(v)); }
            case "dateNaissance" -> m.setDateNaissance(v == null ? null : LocalDate.parse(v));
            case "lieuNaissance" -> m.setLieuNaissance(v);
            case "numeroTelephone" -> m.setNumeroTelephone(v);
            case "email" -> m.setEmail(v);
            case "personneAPrevenir" -> m.setPersonneAPrevenir(v);
            case "personneAPrevenirTelephone" -> m.setPersonneAPrevenirTelephone(v);
            case "statut" -> { if (v != null) m.setStatut(StatutMembreEnum.valueOf(v)); }
            case "nationalite" -> m.setNationalite(v);
            case "groupeSanguin" -> m.setGroupeSanguin(v);
            case "dernierDiplome" -> m.setDernierDiplome(v);
            case "diplomes" -> {
                List<String> l = Diplomes.nettoyer(v == null ? List.of() : Arrays.asList(v.split(SEPARATEUR_DIPLOMES)));
                m.getDiplomes().clear();
                m.getDiplomes().addAll(l);
                m.setDernierDiplome(Diplomes.plusEleve(l));
            }
            case "categorie" -> m.setCategorie(v);
            case "compagnie" -> m.setCompagnie(v);
            case "section" -> m.setSection(v);
            case "brigade" -> m.setBrigade(v);
            case "statutProfessionnel", "profession", "employeur", "secteurActivite" -> {
                SituationProfessionnelle sp = m.getSituationProfessionnelle();
                if (sp == null) {
                    sp = SituationProfessionnelle.builder().membre(m).build();
                    m.setSituationProfessionnelle(sp);
                }
                switch (champ) {
                    case "statutProfessionnel" -> { if (v != null) sp.setStatut(StatutProfessionnelEnum.valueOf(v)); }
                    case "profession" -> sp.setProfession(v);
                    case "employeur" -> sp.setNomEmployeur(v);
                    default -> sp.setSecteurActivite(v);
                }
            }
            case "region", "cercle", "commune", "quartier" -> {
                Localisation l = m.getLocalisation();
                if (l == null) {
                    l = Localisation.builder().membre(m).pays("Mali").build();
                    m.setLocalisation(l);
                }
                switch (champ) {
                    case "region" -> l.setRegion(v);
                    case "cercle" -> l.setCercle(v);
                    case "commune" -> l.setCommune(v);
                    default -> l.setQuartier(v);
                }
            }
            default -> { return false; }
        }
        return true;
    }

    private static String texte(Object o) { return o == null ? null : o.toString(); }

    private static String vide(String s) { return StringUtils.hasText(s) ? s.trim() : null; }
}
