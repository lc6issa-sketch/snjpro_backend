package com.example.snjpro_backend.serviceImpl;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.MessagerieDTOs.*;
import com.example.snjpro_backend.entity.*;
import com.example.snjpro_backend.enumeration.Enumerations.TypeConversation;
import com.example.snjpro_backend.enumeration.RoleEnum;
import com.example.snjpro_backend.repository.*;
import com.example.snjpro_backend.security.PerimetreService;
import com.example.snjpro_backend.serviceInterface.MessagerieServiceInterface;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class MessagerieServiceImpl implements MessagerieServiceInterface {
    private static final int EN_LIGNE_MINUTES = 30;

    private final ConversationRepository conversationRepository;
    private final ParticipantConversationRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final MembreRepository membreRepository;
    private final StockageFichierService stockage;
    private final PerimetreService perimetre;

    @Override @Transactional(readOnly = true)
    public List<ConversationDTO> conversations(String recherche) {
        User moi = perimetre.utilisateurCourant();
        String q = StringUtils.hasText(recherche) ? recherche.trim().toLowerCase() : null;
        return conversationRepository.duParticipant(moi.getId()).stream()
                .map(c -> toDTO(c, moi))
                .filter(c -> q == null || (c.getTitre() + " " + Objects.toString(c.getDernierMessage(), "")).toLowerCase().contains(q))
                .toList();
    }

    @Override @Transactional(readOnly = true)
    public long nonLus() {
        User moi = perimetre.utilisateurCourant();
        return participantRepository.findByUserId(moi.getId()).stream().mapToLong(p -> nonLus(p, moi.getId())).sum();
    }

    @Override
    public ConversationDTO creer(NouvelleConversation dto) {
        User moi = perimetre.utilisateurCourant();
        Conversation c;
        if (StringUtils.hasText(dto.getService())) {
            c = conversationService(moi, dto.getService().trim().toUpperCase());
        } else if (dto.getMembreId() != null || dto.getDestinataireUserId() != null) {
            Long autre = dto.getDestinataireUserId();
            if (dto.getMembreId() != null) {
                Membre m = membreRepository.findById(dto.getMembreId())
                        .orElseThrow(() -> new EntityNotFoundException("Membre non trouvé : " + dto.getMembreId()));
                if (m.getUserId() == null)
                    throw new IllegalArgumentException("Ce membre n'a pas encore activé son compte SNJPro : contactez-le par téléphone ou SMS.");
                autre = m.getUserId();
            }
            if (Objects.equals(autre, moi.getId())) throw new IllegalArgumentException("Vous ne pouvez pas vous écrire à vous-même.");
            User destinataire = userRepository.findById(autre).orElseThrow(() -> new EntityNotFoundException("Utilisateur non trouvé."));
            c = conversationDirecte(moi.getId(), destinataire.getId());
        } else if (!dto.getParticipantsUserIds().isEmpty()) {
            if (!StringUtils.hasText(dto.getTitre())) throw new IllegalArgumentException("Donnez un nom au groupe.");
            c = conversationRepository.save(Conversation.builder().type(TypeConversation.GROUPE).titre(dto.getTitre().trim()).build());
            Set<Long> ids = new LinkedHashSet<>(dto.getParticipantsUserIds());
            ids.add(moi.getId());
            for (Long id : ids) {
                if (!userRepository.existsById(id)) throw new EntityNotFoundException("Utilisateur non trouvé : " + id);
                ajouter(c, id);
            }
        } else {
            throw new IllegalArgumentException("Indiquez un destinataire, un membre, un service ou des participants.");
        }
        if (StringUtils.hasText(dto.getPremierMessage())) {
            NouveauMessage m = new NouveauMessage();
            m.setContenu(dto.getPremierMessage());
            envoyer(c.getId(), m);
        }
        return toDTO(c, moi);
    }

    @Override
    public ConversationDTO groupeDeMaCohorte() {
        User moi = perimetre.utilisateurCourant();
        Membre m = membreRepository.findByUserId(moi.getId())
                .orElseThrow(() -> new AccessDeniedException("Réservé aux membres."));
        Cohorte cohorte = m.getCohorte();
        Conversation c = conversationRepository.findFirstByTypeAndCohorteId(TypeConversation.GROUPE, cohorte.getId())
                .orElseGet(() -> conversationRepository.save(Conversation.builder()
                        .type(TypeConversation.GROUPE).cohorteId(cohorte.getId()).titre(cohorte.getNom()).build()));
        if (participantRepository.findByConversationIdAndUserId(c.getId(), moi.getId()).isEmpty()) ajouter(c, moi.getId());
        return toDTO(c, moi);
    }

    @Override
    public Page<MessageDTO> messages(Long conversationId, Pageable pageable) {
        User moi = perimetre.utilisateurCourant();
        ParticipantConversation p = participation(conversationId, moi.getId());
        LocalDateTime lectureAutres = participantRepository.findByConversationId(conversationId).stream()
                .filter(x -> !x.getUserId().equals(moi.getId()) && x.getDerniereLecture() != null)
                .map(ParticipantConversation::getDerniereLecture).max(LocalDateTime::compareTo).orElse(null);
        Page<MessageDTO> page = messageRepository.findByConversationIdOrderByDateEnvoiDesc(conversationId, pageable)
                .map(m -> toMessage(m, moi.getId(), lectureAutres));
        p.setDerniereLecture(LocalDateTime.now());
        participantRepository.save(p);
        return page;
    }

    @Override
    public MessageDTO envoyer(Long conversationId, NouveauMessage dto) {
        User moi = perimetre.utilisateurCourant();
        ParticipantConversation p = participation(conversationId, moi.getId());
        Message m = enregistrer(p.getConversation(), moi.getId(), dto.getContenu().trim(), null, null, null);
        p.setDerniereLecture(LocalDateTime.now());
        return toMessage(m, moi.getId(), null);
    }

    @Override
    public MessageDTO envoyerPieceJointe(Long conversationId, MultipartFile fichier, String contenu) {
        User moi = perimetre.utilisateurCourant();
        ParticipantConversation p = participation(conversationId, moi.getId());
        String chemin = stockage.enregistrer(fichier, "messagerie/" + conversationId);
        Message m = enregistrer(p.getConversation(), moi.getId(), StringUtils.hasText(contenu) ? contenu.trim() : null,
                chemin, fichier.getOriginalFilename(), fichier.getSize());
        return toMessage(m, moi.getId(), null);
    }

    @Override @Transactional(readOnly = true)
    public Fichier pieceJointe(Long messageId) {
        Message m = messageRepository.findById(messageId).orElseThrow(() -> new EntityNotFoundException("Message non trouvé : " + messageId));
        participation(m.getConversation().getId(), perimetre.utilisateurCourant().getId());
        if (m.getPieceJointeChemin() == null) throw new EntityNotFoundException("Ce message n'a pas de pièce jointe.");
        return new Fichier(stockage.lire(m.getPieceJointeChemin()), m.getPieceJointeNom(), StockageFichierService.typeMime(m.getPieceJointeNom()));
    }

    @Override @Transactional(readOnly = true)
    public List<Contact> contacts(String recherche) {
        User moi = perimetre.utilisateurCourant();
        String q = StringUtils.hasText(recherche) ? recherche.trim().toLowerCase() : null;
        List<Contact> contacts = new ArrayList<>();
        Optional<Membre> moiMembre = membreRepository.findByUserId(moi.getId());
        if (moiMembre.isPresent()) {
            Membre m = moiMembre.get();
            String region = m.getLocalisation() != null ? m.getLocalisation().getRegion() : null;
            if (region != null)
                contacts.add(Contact.builder().type("SERVICE").service("REGIONAL").nom("Direction régionale · " + region)
                        .delaiReponse("Répond en ~2 h").build());
            contacts.add(Contact.builder().type("SERVICE").service("NATIONAL").nom("Direction nationale du SNJ")
                    .delaiReponse("Répond en ~24 h").build());
            membreRepository.findAll().stream()
                    .filter(x -> x.getCohorte().getId().equals(m.getCohorte().getId()) && x.getUserId() != null && !x.getId().equals(m.getId()))
                    .limit(100)
                    .forEach(x -> contacts.add(Contact.builder().type("MEMBRE").userId(x.getUserId()).membreId(x.getId())
                            .nom(x.getPrenom() + " " + x.getNom())
                            .detail(Objects.toString(x.getSituationProfessionnelle() != null ? x.getSituationProfessionnelle().getProfession() : null, "Membre")
                                    + (x.getLocalisation() != null && x.getLocalisation().getRegion() != null ? " · " + x.getLocalisation().getRegion() : ""))
                            .enLigne(enLigne(x.getUserId())).build()));
        } else {
            userRepository.findAll().stream()
                    .filter(u -> u.isActif() && u.getRole() != RoleEnum.ROLE_MEMBRE && !u.getId().equals(moi.getId()))
                    .forEach(u -> contacts.add(Contact.builder().type("UTILISATEUR").userId(u.getId()).nom(nom(u))
                            .detail(libelleRole(u)).enLigne(enLigne(u.getId())).build()));
        }
        return contacts.stream().filter(c -> q == null || (c.getNom() + " " + Objects.toString(c.getDetail(), "")).toLowerCase().contains(q)).toList();
    }

    // =====================================================================
    // Utilitaires
    // =====================================================================

    private Conversation conversationService(User moi, String service) {
        String region = null;
        List<User> agents;
        if ("REGIONAL".equals(service)) {
            Membre m = membreRepository.findByUserId(moi.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Le service régional est accessible depuis un compte membre."));
            region = m.getLocalisation() != null ? m.getLocalisation().getRegion() : null;
            if (region == null) throw new IllegalArgumentException("Renseignez votre région dans votre profil pour écrire à votre direction régionale.");
            String r = region;
            agents = userRepository.findByRole(RoleEnum.ROLE_RESPONSABLE_REGIONAL).stream()
                    .filter(u -> u.isActif() && r.equalsIgnoreCase(u.getRegion())).toList();
        } else if ("NATIONAL".equals(service)) {
            agents = List.of();
        } else {
            throw new IllegalArgumentException("Service inconnu : REGIONAL ou NATIONAL.");
        }
        if (agents.isEmpty()) agents = userRepository.findByRole(RoleEnum.ROLE_ADMIN_SNJ).stream().filter(User::isActif).toList();
        String titre = region != null ? "Direction régionale · " + region : "Direction nationale du SNJ";
        for (ParticipantConversation p : participantRepository.findByUserId(moi.getId())) {
            Conversation c = p.getConversation();
            if (c.getType() == TypeConversation.SERVICE && titre.equals(c.getTitre())) return c;
        }
        Conversation c = conversationRepository.save(Conversation.builder().type(TypeConversation.SERVICE).titre(titre).regionService(region).build());
        ajouter(c, moi.getId());
        agents.forEach(a -> ajouter(c, a.getId()));
        return c;
    }

    private Conversation conversationDirecte(Long a, Long b) {
        for (ParticipantConversation p : participantRepository.findByUserId(a)) {
            Conversation c = p.getConversation();
            if (c.getType() != TypeConversation.DIRECTE) continue;
            List<ParticipantConversation> ps = participantRepository.findByConversationId(c.getId());
            if (ps.size() == 2 && ps.stream().anyMatch(x -> x.getUserId().equals(b))) return c;
        }
        Conversation c = conversationRepository.save(Conversation.builder().type(TypeConversation.DIRECTE).build());
        ajouter(c, a);
        ajouter(c, b);
        return c;
    }

    private void ajouter(Conversation c, Long userId) {
        participantRepository.save(ParticipantConversation.builder().conversation(c).userId(userId).build());
    }

    private Message enregistrer(Conversation c, Long auteur, String contenu, String chemin, String nom, Long taille) {
        if (contenu == null && chemin == null) throw new IllegalArgumentException("Le message est vide.");
        LocalDateTime maintenant = LocalDateTime.now();
        c.setDernierMessageLe(maintenant);
        conversationRepository.save(c);
        return messageRepository.save(Message.builder().conversation(c).auteurId(auteur).contenu(contenu)
                .pieceJointeChemin(chemin).pieceJointeNom(nom).pieceJointeTaille(taille).dateEnvoi(maintenant).build());
    }

    private ParticipantConversation participation(Long conversationId, Long userId) {
        if (!conversationRepository.existsById(conversationId)) throw new EntityNotFoundException("Conversation non trouvée : " + conversationId);
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new AccessDeniedException("Vous ne participez pas à cette conversation."));
    }

    private long nonLus(ParticipantConversation p, Long userId) {
        return p.getDerniereLecture() == null
                ? messageRepository.countByConversationIdAndAuteurIdNot(p.getConversation().getId(), userId)
                : messageRepository.countByConversationIdAndDateEnvoiAfterAndAuteurIdNot(p.getConversation().getId(), p.getDerniereLecture(), userId);
    }

    private boolean enLigne(Long userId) {
        return userRepository.findById(userId).map(u -> u.getDerniereConnexion() != null
                && u.getDerniereConnexion().isAfter(LocalDateTime.now().minusMinutes(EN_LIGNE_MINUTES))).orElse(false);
    }

    private static String nom(User u) {
        String n = (Objects.toString(u.getPrenom(), "") + " " + Objects.toString(u.getNom(), "")).trim();
        return n.isEmpty() ? u.getUsername() : n;
    }

    private static String libelleRole(User u) {
        return switch (u.getRole()) {
            case ROLE_ADMIN_SYSTEME -> "Administrateur";
            case ROLE_ADMIN_SNJ -> "Agent SNJ";
            case ROLE_DIRECTION -> "Direction";
            case ROLE_RESPONSABLE_REGIONAL -> "Responsable régional" + (u.getRegion() != null ? " · " + u.getRegion() : "");
            case ROLE_RESPONSABLE_COHORTE -> "Responsable de cohorte";
            case ROLE_MEMBRE -> "Membre";
            case ROLE_PARTENAIRE -> "Partenaire";
        };
    }

    private ConversationDTO toDTO(Conversation c, User moi) {
        List<ParticipantConversation> ps = participantRepository.findByConversationId(c.getId());
        String titre = c.getTitre();
        String sousTitre = null;
        boolean enLigne = false;
        Long membreId = null;
        if (c.getType() == TypeConversation.DIRECTE) {
            Optional<User> autre = ps.stream().map(ParticipantConversation::getUserId).filter(id -> !id.equals(moi.getId()))
                    .findFirst().flatMap(userRepository::findById);
            if (autre.isPresent()) {
                titre = nom(autre.get());
                Optional<Membre> m = membreRepository.findByUserId(autre.get().getId());
                membreId = m.map(Membre::getId).orElse(null);
                sousTitre = m.map(x -> x.getIdentifiantSnj() + (x.getLocalisation() != null && x.getLocalisation().getRegion() != null
                        ? " · " + x.getLocalisation().getRegion() : "")).orElse(libelleRole(autre.get()));
                enLigne = enLigne(autre.get().getId());
            }
        } else if (c.getType() == TypeConversation.SERVICE) {
            sousTitre = c.getRegionService() != null ? "Répond en ~2 h" : "Répond en ~24 h";
        } else {
            sousTitre = ps.size() + " participant(s)";
        }
        Optional<Message> dernier = messageRepository.findFirstByConversationIdOrderByDateEnvoiDesc(c.getId());
        ParticipantConversation moiP = ps.stream().filter(p -> p.getUserId().equals(moi.getId())).findFirst().orElse(null);
        String initiales = titre == null ? "?" : Arrays.stream(titre.split("[\\s·]+")).filter(StringUtils::hasText)
                .limit(2).map(s -> s.substring(0, 1).toUpperCase()).reduce("", String::concat);
        return ConversationDTO.builder()
                .id(c.getId()).type(c.getType()).titre(titre).sousTitre(sousTitre).initiales(initiales).enLigne(enLigne)
                .dernierMessage(dernier.map(m -> (m.getAuteurId().equals(moi.getId()) ? "Vous : " : "")
                        + (m.getContenu() != null ? m.getContenu() : "📎 " + m.getPieceJointeNom())).orElse(null))
                .dernierMessageLe(c.getDernierMessageLe())
                .nonLus(moiP == null ? 0 : nonLus(moiP, moi.getId()))
                .participants(ps.stream().map(ParticipantConversation::getUserId).toList())
                .membreId(membreId)
                .build();
    }

    private MessageDTO toMessage(Message m, Long moi, LocalDateTime lectureAutres) {
        String auteur = userRepository.findById(m.getAuteurId()).map(MessagerieServiceImpl::nom).orElse("Utilisateur supprimé");
        return MessageDTO.builder()
                .id(m.getId()).conversationId(m.getConversation().getId()).auteurId(m.getAuteurId()).auteur(auteur)
                .deMoi(m.getAuteurId().equals(moi)).contenu(m.getContenu())
                .pieceJointeNom(m.getPieceJointeNom()).pieceJointeTaille(m.getPieceJointeTaille())
                .pieceJointeUrl(m.getPieceJointeChemin() != null ? "/api/messagerie/messages/" + m.getId() + "/piece-jointe" : null)
                .dateEnvoi(m.getDateEnvoi())
                .lu(lectureAutres != null && !m.getDateEnvoi().isAfter(lectureAutres))
                .build();
    }
}
