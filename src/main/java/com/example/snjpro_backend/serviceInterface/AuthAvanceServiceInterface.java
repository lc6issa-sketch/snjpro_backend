package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.AuthAvanceDTOs.*;
import com.example.snjpro_backend.dto.AuthResponse;
import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.UserResponseDTO;
import com.example.snjpro_backend.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

public interface AuthAvanceServiceInterface {
    /** Après vérification du mot de passe : émet le jeton, ou envoie un code SMS si la double authentification est active. */
    AuthResponse apresAuthentification(User user, HttpServletRequest requete);
    AuthResponse emettreJeton(User user, HttpServletRequest requete);
    void fermerSession(String jwt);

    // Connexion
    AuthResponse connexionTelephone(ConnexionTelephone dto, HttpServletRequest requete);
    Map<String, Object> demanderCodeConnexion(DemandeCode dto);
    AuthResponse verifierCodeConnexion(VerificationCodeConnexion dto, HttpServletRequest requete);

    // Mot de passe oublié
    Map<String, Object> motDePasseOublieEmail(Email dto);
    Map<String, Object> motDePasseOublieSms(Telephone dto);
    JetonEtape verifierCodeReinitialisation(VerificationTelephone dto);
    Map<String, String> reinitialiser(Reinitialisation dto);

    // Activation du compte d'un membre (application mobile)
    Map<String, Object> demanderActivation(Telephone dto);
    JetonEtape verifierActivation(VerificationTelephone dto);
    AuthResponse finaliserActivation(Activation dto, HttpServletRequest requete);

    // Invitations
    UserResponseDTO inviter(com.example.snjpro_backend.dto.InvitationDTO dto);
    List<UserResponseDTO> invitationsEnAttente();
    UserResponseDTO renvoyerInvitation(Long userId);
    InfoInvitation infoInvitation(String jeton);
    AuthResponse accepterInvitation(AcceptationInvitation dto, HttpServletRequest requete);

    // Mon compte
    AuthResponse modifierMonCompte(MonCompte dto);
    AuthResponse changerPhoto(MultipartFile photo);
    Fichier maPhoto();
    Fichier photoUtilisateur(Long userId);
    AuthResponse doubleAuthentification(DoubleAuthentification dto);
    List<Session> sessions(String jwtCourant);
    void deconnecterSession(Long id);
    int deconnecterAutresSessions(String jwtCourant);
    List<Preference> preferences();
    List<Preference> enregistrerPreferences(List<Preference> preferences);

    // Vérification du numéro d'un membre connecté (F18)
    Map<String, Object> envoyerCodeTelephone();
    Map<String, Object> verifierTelephone(Code dto);
}
