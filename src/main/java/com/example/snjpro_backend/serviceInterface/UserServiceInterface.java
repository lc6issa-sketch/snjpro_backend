package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.ChangementMotDePasseDTO;
import com.example.snjpro_backend.dto.UserDTO;
import com.example.snjpro_backend.dto.UserResponseDTO;
import com.example.snjpro_backend.enumeration.RoleEnum;

import java.util.List;

public interface UserServiceInterface {
    UserResponseDTO creerUtilisateur(UserDTO userDTO, String createdBy);
    UserResponseDTO modifierUtilisateur(Long id, UserDTO userDTO, String utilisateurCourant);
    UserResponseDTO obtenirUtilisateur(Long id);
    UserResponseDTO obtenirParUsername(String username);
    UserResponseDTO obtenirParEmail(String email);
    List<UserResponseDTO> obtenirTousLesUtilisateurs(RoleEnum role, Boolean actif);
    UserResponseDTO activerDesactiverUtilisateur(Long id, boolean actif, String utilisateurCourant);
    void supprimerUtilisateur(Long id, String utilisateurCourant);
    void changerMotDePasse(String username, ChangementMotDePasseDTO dto);
    UserResponseDTO reinitialiserMotDePasse(Long id, String nouveauMotDePasse);
    void enregistrerConnexion(Long id);
    boolean existeParUsername(String username);
    boolean existeParEmail(String email);
}
