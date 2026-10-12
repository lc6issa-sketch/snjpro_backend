package com.example.snjpro_backend.dto;

import com.example.snjpro_backend.enumeration.SexeEnum;
import com.example.snjpro_backend.enumeration.StatutMembreEnum;
import com.example.snjpro_backend.enumeration.StatutProfessionnelEnum;
import lombok.Data;

@Data
public class MembreFilterDTO {
    private String query;
    private Long cohorteId;
    private StatutMembreEnum statut;
    private SexeEnum sexe;
    private String region;
    private String competence;
    private StatutProfessionnelEnum statutProfessionnel;
    private String profession;
    private String secteurActivite;
}