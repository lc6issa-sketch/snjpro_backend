package com.example.snjpro_backend.serviceInterface;

import com.example.snjpro_backend.dto.DocumentDTOs.Fichier;
import com.example.snjpro_backend.dto.ImportAssistantDTOs.*;
import com.example.snjpro_backend.enumeration.Enumerations.DecisionDoublon;
import com.example.snjpro_backend.enumeration.Enumerations.FormatFichier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

public interface ImportAssistantServiceInterface {
    Analyse analyser(MultipartFile fichier, Long cohorteId);
    Page<Session> lister(Pageable pageable);
    Analyse obtenir(Long id);
    Analyse definirCorrespondance(Long id, Map<String, String> correspondance);
    Verification verifier(Long id);
    DoublonLigne decider(Long id, int ligne, DecisionDoublon decision);
    Verification deciderTous(Long id, DecisionDoublon decision);
    Resultat importer(Long id);
    Fichier exporterErreurs(Long id, FormatFichier format);
    Fichier rapport(Long id);
    void supprimer(Long id);
}
