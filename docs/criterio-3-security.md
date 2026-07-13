# Criterio 3 — Implementa seguridad en el backend con Spring Security (15 pts)

## Objetivo del Criterio

Implementar seguridad en el backend asegurando roles, permisos y protección de todos los endpoints, integrando la autenticación con Azure AD B2C mediante tokens JWT.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Backend securitizado mediante Spring Security en todos los endpoints | ✅ | SecurityConfig.java protege todas las rutas `/api/**` |
| 2 roles: uno para descargar guías, otro para el resto | ✅ | Rol `consulta` (descargar) y rol `admin` (resto) |
| Validación de tokens JWT con Azure AD B2C | ✅ | `.oauth2ResourceServer().jwt()` en SecurityConfig |
| Claims personalizados desde Azure AD B2C | ✅ | `extension_consultaRole` extraído del JWT |
| Endpoints protegidos por rol en el código | ✅ | `@PreAuthorize` en Controller + reglas en SecurityConfig |
| Integración con el API Gateway | ✅ | El API Gateway también valida JWT (Criterio 4) |
| Toggle de seguridad para desarrollo local | ✅ | `app.security.enabled=false/true` |
| Modo stateless (sin sesiones HTTP, solo JWT) | ✅ | `SessionCreationPolicy.STATELESS` |

---

## Paso 1: Dependencias en pom.xml

Las siguientes dependencias ya están incluidas:

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- OAuth2 Resource Server (valida JWT de Azure AD B2C) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-oauth2-resource-server</artifactId>
</dependency>

<!-- Test de seguridad -->
<dependency>
    <groupId>org.springframework.security</groupId>
    <artifactId>spring-security-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## Paso 2: Configurar application.properties

```properties
# ============================================================
# CONTROL DE SEGURIDAD
# false → modo prueba (sin JWT, desarrollo local)
# true  → modo producción (valida JWT con Azure AD B2C)
# ============================================================
app.security.enabled=${APP_SECURITY_ENABLED:false}

# ============================================================
# AZURE AD B2C — Autenticación JWT
# Completar con valores reales al configurar Azure (Criterio 5)
# ============================================================
spring.security.oauth2.resourceserver.jwt.issuer-uri=${AZURE_ISSUER_URI:...}
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${AZURE_JWKS_URI:...}
```

---

## Paso 3: Implementar SecurityConfig.java

**Archivo:** `src/main/java/com/transportista/config/SecurityConfig.java`

### 3.1 Estructura de la configuración

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity                // Habilita @PreAuthorize
public class SecurityConfig {

    @Value("${app.security.enabled:false}")
    private boolean securityEnabled;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
            .csrf(csrf -> csrf.disable())         // APIs REST no necesitan CSRF
            .cors(cors -> ...)                     // Permitir peticiones desde cualquier origen
            .sessionManagement(session ->
                session.sessionCreationPolicy(     // Sin sesiones HTTP
                    SessionCreationPolicy.STATELESS));

        if (securityEnabled) {
            // ═══ MODO PRODUCCIÓN ═══
            http
                .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/actuator/health").permitAll()
                    // SOLO rol consulta puede descargar guías
                    .requestMatchers(HttpMethod.GET, "/api/guias/*/descargar")
                        .hasAuthority("consulta")
                    // SOLO rol admin puede el resto
                    .requestMatchers("/api/**").hasAuthority("admin")
                    .anyRequest().authenticated()
                )
                .oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt.jwtAuthenticationConverter(
                        jwtAuthenticationConverter()));
        } else {
            // ═══ MODO PRUEBA ═══
            http.authorizeHttpRequests(auth -> auth
                .anyRequest().permitAll());
        }
        return http.build();
    }
}
```

### 3.2 Matriz de acceso por rol

| Endpoint | `admin` | `consulta` | Sin auth |
|----------|:-------:|:----------:|:--------:|
| `POST /api/guias` | ✅ 201 | ❌ 403 | ❌ 401 |
| `PUT /api/guias/{id}` | ✅ 200 | ❌ 403 | ❌ 401 |
| `DELETE /api/guias/{id}` | ✅ 204 | ❌ 403 | ❌ 401 |
| `GET /api/guias` | ✅ 200 | ❌ 403 | ❌ 401 |
| `POST /api/guias/{id}/subir-s3` | ✅ 200 | ❌ 403 | ❌ 401 |
| `POST /api/cola/procesar-guias` | ✅ 200 | ❌ 403 | ❌ 401 |
| `GET /api/guias/{id}/descargar` | ❌ 403 | ✅ 200 | ❌ 401 |

### 3.3 Extracción de roles desde el JWT

```java
@Bean
public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
        // Lee el custom claim de Azure AD B2C:
        // "extension_consultaRole" = "consulta" o "admin"
        String roleClaim = jwt.getClaimAsString("extension_consultaRole");

        List<GrantedAuthority> authorities = new ArrayList<>();
        if (roleClaim != null) {
            if (roleClaim.toLowerCase().contains("admin"))
                authorities.add(new SimpleGrantedAuthority("admin"));
            if (roleClaim.toLowerCase().contains("consulta"))
                authorities.add(new SimpleGrantedAuthority("consulta"));
        }
        return authorities;
    });
    return converter;
}
```

### 3.4 Configuración CORS

```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of("*"));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    // En producción: restringir a dominios específicos
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}
```

---

## Paso 4: Flujo de autenticación JWT con Azure AD B2C

```
┌──────────┐      ┌─────────────────┐      ┌──────────────────┐      ┌──────────────┐
│  Cliente  │      │  Azure AD B2C   │      │  Transportista   │      │  Endpoint    │
│ (Postman) │      │  (IDaaS)        │      │  App (Backend)   │      │  protegido   │
└─────┬─────┘      └────────┬────────┘      └────────┬─────────┘      └──────┬───────┘
      │                     │                        │                        │
      │ 1. POST /login      │                        │                        │
      │────────────────────▶│                        │                        │
      │                     │                        │                        │
      │ 2. JWT (firmado)    │                        │                        │
      │◀────────────────────│                        │                        │
      │                     │                        │                        │
      │ 3. GET /api/guias   │                        │                        │
      │    Authorization:   │                        │                        │
      │    Bearer <JWT>     │                        │                        │
      │─────────────────────────────────────────────▶│                        │
      │                     │                        │                        │
      │                     │  4. Valida firma JWT   │                        │
      │                     │  (jwks_uri de Azure)   │                        │
      │                     │◀───────────────────────│                        │
      │                     │                        │                        │
      │                     │  5. Firma válida ✅     │                        │
      │                     │────────────────────────▶│                        │
      │                     │                        │                        │
      │                     │                        │  6. Extrae claim       │
      │                     │                        │  extension_consultaRole│
      │                     │                        │  → "admin"             │
      │                     │                        │                        │
      │                     │                        │  7. Verifica @PreAuth  │
      │                     │                        │  hasAuthority("admin")  │
      │                     │                        │───────────────────────▶│
      │                     │                        │                        │
      │                     │                        │  8. 200 OK + datos      │
      │                     │                        │◀───────────────────────│
      │                     │                        │                        │
      │ 9. Respuesta JSON   │                        │                        │
      │◀─────────────────────────────────────────────│                        │
```

---

## Paso 5: Toggle de seguridad (desarrollo vs producción)

### Modo desarrollo (app.security.enabled=false)

- **Uso:** mientras desarrollas localmente sin Azure AD B2C configurado
- **Comportamiento:** todos los endpoints son accesibles sin token
- **Perfil:** `dev` (`application-dev.properties`)

### Modo producción (app.security.enabled=true)

- **Uso:** cuando tienes Azure AD B2C configurado y quieres probar seguridad real
- **Comportamiento:** se requiere token JWT válido de Azure AD B2C
- **Perfil:** por defecto (sin perfil) o cualquier perfil que no sea `dev`

### Cambiar entre modos:

```bash
# Desarrollo (sin seguridad)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Producción (con seguridad)
export APP_SECURITY_ENABLED=true
./mvnw spring-boot:run
```

---

## Verificación del Criterio

### Tests de seguridad:

| Clase de test | Tests | ¿Qué valida? |
|---------------|:-----:|--------------|
| `GuiaDespachoControllerTest$SeguridadTests` | 7 | Accesos con/sin rol (401, 403), matriz de permisos |
| `GuiaDespachoControllerTest$CrudEndpointsTests` | 8 | Endpoints protegidos solo accesibles con rol admin |
| `GuiaDespachoControllerTest$DescargaEndpointTests` | 2 | Endpoint de descarga solo accesible con rol consulta |
| `SecurityConfigIntegrationTest$ExtraccionAutoridades` | 3 | Extracción de roles desde claims del JWT |
| `TransportistaApplicationTests` (seguridad) | 3 | Beans SecurityFilterChain, JwtConverter, CorsConfig |

### Ejecutar tests de seguridad:

```bash
./mvnw test -DforkCount=0 -Dtest="GuiaDespachoControllerTest,SecurityConfigIntegrationTest"
```

### Evidencia para la documentación Word:

1. **Modo prueba (sin JWT):**
   - Captura de Postman: `GET /api/guias` → 200 OK sin token
2. **Modo producción (sin token):**
   - Captura de Postman: `GET /api/guias` → 401 Unauthorized
3. **Con token de rol consulta:**
   - Captura de Postman: `GET /api/guias/1/descargar` → 200 OK
   - Captura de Postman: `POST /api/guias` → 403 Forbidden (no tiene permiso)
4. **Con token de rol admin:**
   - Captura de Postman: `POST /api/guias` → 201 Created
   - Captura de Postman: `GET /api/guias/1/descargar` → 403 Forbidden (no tiene permiso)
5. Captura de `jwt.ms` mostrando el claim `extension_consultaRole`
6. Captura del código `SecurityConfig.java` con las reglas de seguridad
7. Captura de logs de Spring Boot mostrando "[SECURITY] JWT validado | Roles: [admin]"

---

## Checklist de Verificación

- [x] `SecurityConfig.java` implementado con reglas por rol
- [x] Toggle `app.security.enabled` para desarrollo/producción
- [x] Matriz de acceso: admin puede todo menos descargar, consulta solo descargar
- [x] Extracción de roles desde claim `extension_consultaRole`
- [x] Validación JWT con Azure AD B2C (`.oauth2ResourceServer().jwt()`)
- [x] Stateless session (sin sesiones HTTP)
- [x] CORS configurado para APIs REST
- [x] `@EnableMethodSecurity` habilitado para `@PreAuthorize`
- [x] CSRF deshabilitado (no necesario para APIs JWT)
- [x] Tests de seguridad pasando (23 tests de seguridad)
- [ ] Completar `AZURE_ISSUER_URI` y `AZURE_JWKS_URI` en properties (Criterio 5)
- [ ] Probar con tokens reales de Azure AD B2C (paso de producción)
