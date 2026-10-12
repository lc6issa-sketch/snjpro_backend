package com.example.snjpro_backend.serviceImpl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Envoi des SMS et e-mails (codes de vérification, mobilisations, invitations…).
 * <p>
 * Aucun fournisseur n'est encore branché (passerelle SMS, serveur SMTP) : les messages sont écrits
 * dans les logs. Pour la mise en production, remplacer le corps de {@link #sms} et {@link #email}
 * par l'appel au fournisseur retenu ; le reste de l'application n'a pas à changer.
 */
@Component
@Slf4j
public class EnvoiMessageService {

    /** @return true si le message a été confié au canal d'envoi */
    public boolean sms(String telephone, String message) {
        if (!StringUtils.hasText(telephone)) return false;
        log.info("[SMS → {}] {}", telephone, message);
        return true;
    }

    public boolean email(String destinataire, String sujet, String corps) {
        if (!StringUtils.hasText(destinataire)) return false;
        log.info("[E-MAIL → {}] {} — {}", destinataire, sujet, corps);
        return true;
    }
}
