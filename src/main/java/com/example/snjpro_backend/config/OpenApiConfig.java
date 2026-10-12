package com.example.snjpro_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "SNJPro API",
                version = "1.0.0",
                description = """
                        API REST du Système d'Information du Service National des Jeunes du Mali.

                        **Pour tester :**
                        1. `POST /api/auth/login` avec `{"username": "admin", "password": "Admin@2026"}`
                        2. Copier la valeur `token` de la réponse
                        3. Cliquer sur **Authorize** (cadenas en haut à droite) et coller le token (sans « Bearer »)
                        """,
                contact = @Contact(name = "SNJ Mali", email = "admin@snj.ml")
        ),
        servers = {
                @Server(url = "http://localhost:8080", description = "Serveur local")
        }
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "Token JWT obtenu via POST /api/auth/login"
)
public class OpenApiConfig {
}
