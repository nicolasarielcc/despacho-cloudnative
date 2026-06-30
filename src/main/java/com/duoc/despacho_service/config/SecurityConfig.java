package com.duoc.despacho_service.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collections;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/guias").hasRole("gestion")
                .requestMatchers(HttpMethod.GET, "/api/guias").hasRole("gestion")
                .requestMatchers(HttpMethod.GET, "/api/guias/{id}").hasRole("gestion")
                .requestMatchers(HttpMethod.PUT, "/api/guias/{id}").hasRole("gestion")
                .requestMatchers(HttpMethod.DELETE, "/api/guias/{id}").hasRole("gestion")
                .requestMatchers(HttpMethod.GET, "/api/guias/{id}/descargar").hasAnyRole("descarga", "gestion")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );

        return http.build();
    }

    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            String extensionRol = jwt.getClaimAsString("extension_rol");

            if (extensionRol == null) {
                extensionRol = jwt.getClaimAsString("rol");
            }

            if (extensionRol != null) {
                return List.of(new SimpleGrantedAuthority("ROLE_" + extensionRol));
            }

            return Collections.emptyList();
        });
        return converter;
    }
}
