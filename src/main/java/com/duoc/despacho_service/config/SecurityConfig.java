package com.duoc.despacho_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Nombre exacto del custom claim creado en Azure AD B2C
    private static final String CLAIM_ROL = "extension_rolDespacho";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/h2-console/**").permitAll()
                // Descargar guías: solo rol "descarga"
                .requestMatchers(HttpMethod.GET, "/api/guias/*/descargar")
                    .hasAuthority("descarga")
                // Resto de endpoints: solo rol "gestion"
                .requestMatchers(HttpMethod.POST,   "/api/guias").hasAuthority("gestion")
                .requestMatchers(HttpMethod.POST,   "/api/guias/*/subir").hasAuthority("gestion")
                .requestMatchers(HttpMethod.PUT,    "/api/guias/*").hasAuthority("gestion")
                .requestMatchers(HttpMethod.DELETE, "/api/guias/*").hasAuthority("gestion")
                .requestMatchers(HttpMethod.GET,    "/api/guias/buscar").hasAuthority("gestion")
                .anyRequest().authenticated()
            )
            .headers(headers -> headers
                .frameOptions(frame -> frame.sameOrigin()))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

        return http.build();
    }

    // Convierte el custom claim de Azure AD B2C en authorities de Spring Security.
    // Azure emite el claim como "extension_rolDespacho": "descarga" o "gestion".
    // Este converter lo mapea directamente como authority, sin prefijo SCOPE_ ni ROLE_.
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName(CLAIM_ROL);
        authoritiesConverter.setAuthorityPrefix("");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}