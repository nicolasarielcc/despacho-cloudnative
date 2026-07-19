package com.transportista.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

/**
 * Tests de integración para SecurityConfig (Criterio 3).
 *
 * Verifica el comportamiento de la cadena de filtros de seguridad:
 * 1. Carga del contexto Spring Boot con perfil test
 * 2. Extracción de roles desde el claim extension_consultaRole
 * 3. Mapeo correcto de autoridades (admin/consulta)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.autoconfigure.exclude=io.awspring.cloud.autoconfigure.s3.S3AutoConfiguration")
@DisplayName("SecurityConfig — Tests de seguridad JWT")
class SecurityConfigIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtAuthenticationConverter converter;

    @Test
    @DisplayName("El contexto Spring Boot carga correctamente con perfil test")
    void contextLoads() {
        assert mockMvc != null : "MockMvc debería ser inyectado";
    }

    @Nested
    @DisplayName("Extracción de autoridades desde JWT")
    class ExtraccionAutoridades {

        @Test
        @DisplayName("JWT con extension_consultaRole='admin' → autoridad instructor")
        void jwtConClaimAdmin_debeTenerAutoridadInstructor() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .claim("extension_consultaRole", "admin")
                    .claim("sub", "user123")
                    .issuer("https://test-tenant.onmicrosoft.com/v2.0/")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            Authentication auth = converter.convert(jwt);
            assert auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("instructor"::equals);
        }

        @Test
        @DisplayName("JWT con extension_consultaRole='consulta' → autoridad estudiante")
        void jwtConClaimConsulta_debeTenerAutoridadEstudiante() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .claim("extension_consultaRole", "consulta")
                    .claim("sub", "user456")
                    .issuer("https://test-tenant.onmicrosoft.com/v2.0/")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            Authentication auth = converter.convert(jwt);
            assert auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .anyMatch("estudiante"::equals);
        }

        @Test
        @DisplayName("JWT sin claim extension_consultaRole → autoridades vacías")
        void jwtSinClaim_debeTenerAutoridadesVacias() {
            Jwt jwt = Jwt.withTokenValue("mock-token")
                    .header("alg", "RS256")
                    .claim("sub", "user789")
                    .issuer("https://test-tenant.onmicrosoft.com/v2.0/")
                    .issuedAt(Instant.now())
                    .expiresAt(Instant.now().plusSeconds(3600))
                    .build();

            Authentication auth = converter.convert(jwt);
            assert auth.getAuthorities().isEmpty();
        }
    }
}
