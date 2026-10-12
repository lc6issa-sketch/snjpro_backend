package com.example.snjpro_backend.enumeration;

/**
 * Énumérations des modules communication, mobilisation, documents, demandes et messagerie.
 */
public final class Enumerations {
    private Enumerations() { }

    public enum TypeCodeVerification { CONNEXION, ACTIVATION, REINITIALISATION, VERIFICATION_TELEPHONE, INVITATION }

    public enum StatutMobilisation { BROUILLON, PLANIFIEE, EN_COURS, CLOTUREE }
    public enum ReponseMobilisation { EN_ATTENTE, DISPONIBLE, INDISPONIBLE }

    public enum TypeNotification { INFORMATION, URGENT, MOBILISATION, ACTUALITE }
    public enum CibleNotification { TOUS, REGION, COHORTE, SELECTION }
    public enum StatutNotification { BROUILLON, PROGRAMMEE, ENVOYEE }

    public enum CategorieActualite { ANNONCE, OPPORTUNITE, MOBILISATION, RAPPORT }
    public enum StatutActualite { BROUILLON, PROGRAMMEE, PUBLIEE, ARCHIVEE }

    public enum TypeOpportunite { EMPLOI, STAGE, FORMATION }
    public enum StatutOpportunite { BROUILLON, OUVERTE, CLOTUREE }
    public enum StatutCandidature { NOUVELLE, PRESELECTIONNEE, ENTRETIEN, RETENUE, NON_RETENUE }

    public enum VisibiliteDocument { MEMBRES, INTERNE, PUBLIC }
    public enum TypeDocument { ATTESTATION, DIPLOME, IDENTITE, PHOTO, CARTE_MEMBRE, CV, AUTRE }

    public enum TypeDemande { MISE_A_JOUR_PROFIL, ATTESTATION, CARTE_MEMBRE, RECLAMATION, AUTRE }
    public enum StatutDemande { EN_ATTENTE, EN_COURS, COMPLEMENT_DEMANDE, ACCEPTEE, REJETEE }

    public enum StatutActivite { PLANIFIEE, EN_COURS, TERMINEE }
    public enum ModePresence { QR_CODE, CODE, MANUEL }

    public enum TypeConversation { DIRECTE, GROUPE, SERVICE }

    public enum StatutImport { ANALYSE, VERIFIE, TERMINE, ANNULE }
    public enum DecisionDoublon { IGNORER, FUSIONNER, CONSERVER_LES_DEUX }

    public enum FormatFichier { XLSX, CSV, PDF }
    public enum TypeRapport { EFFECTIFS_COHORTES, COMPETENCES, MOBILISATIONS, PROFILS_INCOMPLETS, STATISTIQUES_COHORTES }

    /** Souvenirs de formation : EN_ATTENTE (envoyé par un membre, à modérer), PUBLIE, REJETE. */
    public enum StatutSouvenir { EN_ATTENTE, PUBLIE, REJETE }
    public enum TypeMedia { PHOTO, VIDEO, AUDIO }
}
