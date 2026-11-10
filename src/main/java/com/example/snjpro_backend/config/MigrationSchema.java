package com.example.snjpro_backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Ajustements de schéma que {@code ddl-auto=update} ne sait pas faire.
 * <p>
 * Hibernate crée les colonnes d'énumération en type MySQL {@code ENUM(...)} et ne les modifie plus ensuite :
 * toute nouvelle valeur (ex. ROLE_DIRECTION, EN_FORMATION) serait refusée par la base.
 * Ces colonnes sont donc converties en VARCHAR une fois pour toutes. Les requêtes sont idempotentes.
 */
@Component
@Order(0)
@RequiredArgsConstructor
@Slf4j
public class MigrationSchema implements CommandLineRunner {
    private final JdbcTemplate jdbc;

    @Override
    public void run(String... args) {
        executer("ALTER TABLE utilisateurs MODIFY COLUMN role VARCHAR(40) NOT NULL");
        executer("ALTER TABLE cohortes MODIFY COLUMN statut VARCHAR(30) NOT NULL");
        executer("ALTER TABLE membres MODIFY COLUMN statut VARCHAR(20) NOT NULL");
        // Si-SNJ : statuts de cohorte réduits à EN_FORMATION / INCORPOREE / EN_FONCTION (+ ARCHIVEE par l'action d'archivage)
        executer("UPDATE cohortes SET statut = 'EN_FONCTION' WHERE statut IN ('EN_RESERVE', 'LIBEREE', 'ACTIVE', 'TERMINEE')");
        // Si-SNJ : aptitude des membres (APTE par défaut pour les fiches existantes)
        executer("UPDATE membres SET aptitude = 'APTE' WHERE aptitude IS NULL");
    }

    private void executer(String sql) {
        try {
            jdbc.execute(sql);
        } catch (Exception e) {
            log.debug("Migration ignorée ({}) : {}", sql, e.getMessage());
        }
    }
}
