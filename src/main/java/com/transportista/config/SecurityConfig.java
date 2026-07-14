package com.transportista.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuración de Spring Security para validación JWT con Azure AD B2C.
 *
 * Soporta dos modos mediante la propiedad app.security.enabled:
 *
 *   1. MODO PRUEBA (app.security.enabled=false):
 *      - NO valida JWT (perfecto para pruebas locales sin Azure)
 *      - Todos los endpoints son libres
 *      - Usar mientras se configura Azure AD B2C
 *
 *   2. MODO PRODUCCIÓN (app.security.enabled=true):
 *      - Valida tokens JWT emitidos por Azure AD B2C
 *      - Extrae roles del custom claim "extension_consultaRole"
 *      - Protege endpoints según roles admin/consulta
 *
 * IMPORTANTE para producción:
 *   Configurar en application.properties:
 *   - spring.security.oauth2.resourceserver.jwt.issuer-uri
 *   - spring.security.oauth2.resourceserver.jwt.jwk-set-uri
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // ============================================================
    // Control modo seguridad (app.security.enabled)
    //   false → sin JWT, ideal para pruebas locales
    //   true  → valida JWT con Azure AD B2C
    //
    // En application.properties poner:
    //   app.security.enabled=false  (desarrollo local)
    //   app.security.enabled=true   (producción en EC2)
    // ============================================================
    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    /**
     * Cadena de filtros de seguridad.
     * Se adapta según app.security.enabled.
     */
    @Bean
    @SuppressWarnings("unused")
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        if (securityEnabled) {
            // ============================================================
            // MODO PRODUCCIÓN — Seguridad con Azure AD B2C activa
            // ============================================================
            http
                .authorizeHttpRequests(auth -> auth
                    // Endpoints públicos (health check de Actuator)
                    .requestMatchers("/actuator/health").permitAll()

                    // Solo rol consulta puede descargar guías
                    .requestMatchers(HttpMethod.GET, "/api/guias/*/descargar").hasAuthority("consulta")

                    // Solo rol admin puede acceder al resto de endpoints API
                    .requestMatchers("/api/**").hasAuthority("admin")

                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                        .jwtAuthenticationConverter(jwtAuthenticationConverter())
                    )
                );
        } else {
            // ============================================================
            // MODO PRUEBA — Sin seguridad (para desarrollo local)
            // QUITAR en producción
            // ============================================================
            http
                .authorizeHttpRequests(auth -> auth
                    .anyRequest().permitAll()
                );
        }

        return http.build();
    }

    /**
     * Convierte los claims del JWT en autoridades de Spring Security.
     *
     * Lee el custom claim "extension_consultaRole" del token JWT
     * y lo mapea a las autoridades "admin" y/o "consulta".
     *
     * Configuración requerida en Azure AD B2C:
     * 1. User Attributes → Add → extension_consultaRole (String)
     * 2. User Flows → incluir extension_consultaRole en "Return claims"
     * 3. Al crear usuarios, asignar "admin" o "consulta" como valor
     */
    @Bean
    @SuppressWarnings("unused")
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();

        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);

        return converter;
    }

    /**
     * Extrae los roles desde el custom claim "extension_consultaRole" del JWT.
     *
     * @param jwt el token JWT decodificado
     * @return lista de autoridades (roles) de Spring Security
     */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        String roleClaim = jwt.getClaimAsString("extension_consultaRole");

        if (roleClaim == null || roleClaim.isBlank()) {
            // Si no hay claim de rol, se deniega el acceso
            logJwtInfo(jwt, Collections.emptyList());
            return Collections.emptyList();
        }

        List<GrantedAuthority> authorities = new ArrayList<>();
        String roleLower = roleClaim.toLowerCase();

        if (roleLower.contains("admin")) {
            authorities.add(new SimpleGrantedAuthority("admin"));
        }
        if (roleLower.contains("consulta")) {
            authorities.add(new SimpleGrantedAuthority("consulta"));
        }

        logJwtInfo(jwt, authorities);
        return authorities;
    }

    /**
     * Registra en consola los datos relevantes del JWT (debug).
     */
    private void logJwtInfo(Jwt jwt, Collection<GrantedAuthority> authorities) {
        String roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.joining(", "));
        System.out.printf("[SECURITY] JWT validado | Subject: %s | Roles: [%s]%n",
                jwt.getSubject(), roles.isEmpty() ? "NINGUNO" : roles);
    }

    /**
     * Configuración CORS para permitir peticiones desde cualquier origen.
     * En producción, restringir a dominios específicos.
     */
    @Bean
    @SuppressWarnings("unused")
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("*"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
