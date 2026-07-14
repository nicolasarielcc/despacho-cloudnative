# Criterio 5 — Configura la autenticación con Azure AD B2C (15 pts)

## Estado: COMPLETADO

### Configuración Azure AD B2C

| Elemento | Valor |
|----------|-------|
| Tenant | despachoservice2.onmicrosoft.com |
| Tenant ID | 5199d2b5-40ed-44c1-a8e5-f4a83132a743 |
| App registrada | despacho-service-api2 |
| Client ID | 0a27f262-a186-457f-b7b5-eb43b284cd4c |
| App ID URI | https://despachoservice2.onmicrosoft.com/0a27f262-a186-457f-b7b5-eb43b284cd4c |
| Scope | despacho.acceso |
| Client Secret | secreto-despacho (ID: f910b4c5-1b23-430d-a397-a31b14a8aa50) |
| Custom Attribute | consultaRole → en JWT: extension_consultaRole |
| User Flow | B2C_1_despacho_signin (Sign up and sign in) |
| Issuer | https://despachoservice2.b2clogin.com/5199d2b5-40ed-44c1-a8e5-f4a83132a743/v2.0/ |
| JWKS | https://despachoservice2.b2clogin.com/despachoservice2.onmicrosoft.com/discovery/v2.0/keys?p=B2C_1_despacho_signin |

### Usuarios de prueba

| Email | Rol | Sub |
|-------|-----|-----|
| nikocarambas@gmail.com | admin | 1876dbca-73f7-4d2a-b934-26490a835bec |
| ni.cavieres@duocuc.cl | consulta | 35bbc032-4923-4065-99e1-c528cf625f73 |

### Integración con Spring Security

```properties
# application.properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://despachoservice2.b2clogin.com/5199d2b5-40ed-44c1-a8e5-f4a83132a743/v2.0/
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://despachoservice2.b2clogin.com/despachoservice2.onmicrosoft.com/discovery/v2.0/keys?p=B2C_1_despacho_signin
```

- SecurityConfig.java extrae `extension_consultaRole` del JWT y mapea a roles `admin` / `consulta`
- `app.security.enabled=true` activa validación JWT en backend
- `@PreAuthorize("hasAuthority('admin')")` protege endpoints CRUD
- `hasAuthority('consulta')` protege endpoint de descarga

### Integración con API Gateway

- Lambda Authorizer `b2cJwtAuthorizer` (Python 3.12) valida JWT en el edge
- Valida firma RS256 contra JWKS de B2C
- Verifica issuer, audience, expiración
- Extrae rol del claim `extension_consultaRole` y lo pasa como contexto
- Rechaza sin token: 401
- Autorizador asignado a las 8 rutas del API Gateway

### Limitación conocida

El autorizador JWT nativo de AWS API Gateway HTTP API no funciona con Azure AD B2C porque:
- El issuer en el JWT es `.../5199d2b5.../v2.0/` (formato GUID)
- El endpoint de discovery `.well-known/openid-configuration` solo está disponible en `.../tfp/5199d2b5.../b2c_1_despacho_signin/v2.0/`
- API Gateway requiere que `{issuer}/.well-known/openid-configuration` sea accesible
- Solución: Lambda Authorizer personalizado que valida manualmente

### Flujo de autenticación

```
Cliente → POST /api/guias + JWT Bearer
  → API Gateway (Lambda Authorizer)
    → Valida JWT con JWKS de B2C
    → ¿Válido?
      ├── NO → 401 Unauthorized
      └── SÍ → Forward a EC2:8080
        → Spring Security valida JWT (modo producción)
          → ¿Rol autorizado?
            ├── NO → 403 Forbidden
            └── SÍ → Endpoint procesa request
```

### Evidencia para documentación

1. ✅ Tenant B2C creado en Azure Portal
2. ✅ App registration despacho-service-api2 configurada
3. ✅ Custom claim `consultaRole` (extension_consultaRole) creado
4. ✅ User Flow B2C_1_despacho_signin con claim personalizado
5. ✅ Scope `despacho.acceso` expuesto en API
6. ✅ Client Secret creado
7. ✅ Usuario admin creado y JWT verificado
8. ✅ Usuario consulta creado y JWT verificado
9. ✅ Lambda Authorizer desplegado y funcionando en API Gateway
10. ✅ API Gateway rechaza sin token (401) y acepta con JWT (200)
