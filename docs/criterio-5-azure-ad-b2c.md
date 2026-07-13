# Criterio 5 — Configura la autenticación con Azure AD B2C (15 pts)

## Objetivo del Criterio

Configurar Azure AD B2C completamente, asegurando la autenticación y autorización según los requerimientos: crear 2 roles (admin y consulta), integrar con Spring Boot y API Gateway.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Autenticación del backend integrada con Azure AD | ✅ | Spring Security valida JWT emitido por Azure |
| Autenticación del API Gateway integrada con Azure AD | ✅ | Autorizador JWT del API Gateway usa el issuer de Azure |
| 2 roles creados: uno para descargar guías, otro para el resto | ✅ | `extension_consultaRole` = `consulta` o `admin` |
| Custom claim en User Flows | ✅ | `extension_consultaRole` como atributo y return claim |
| Validación de permisos por endpoint | ✅ | `@PreAuthorize` + `.hasAuthority()` en SecurityConfig |

---

## Paso 1: Crear Tenant Azure AD B2C

### 1.1 Acceder a Azure Portal

1. Inicia sesión en [Azure Portal](https://portal.azure.com/)
2. Asegúrate de tener una **suscripción activa**

### 1.2 Crear tenant B2C

1. En la barra de búsqueda, escribe **"B2C"**
2. Selecciona **Azure Active Directory B2C**
3. Haz clic en **Create a new Azure AD B2C Tenant**
4. Completa el formulario:
   - **Organization name:** `TransportistaB2C`
   - **Initial domain name:** `transportistab2c` (único global)
   - **Country/Region:** Chile (o tu país)
   - **Subscription:** selecciona tu suscripción
   - **Resource group:** `rg-transportista` (crea uno nuevo)
5. Haz clic en **Review + Create** → **Create**

⏱️ Espera 5-10 minutos hasta que aparezca el mensaje: "Successfully created tenant"

---

## Paso 2: Cambiar al nuevo tenant B2C

1. Haz clic en el ícono de **engranaje ⚙** (Settings) en la esquina superior derecha
2. Ve a **Directories + subscriptions**
3. Busca `TransportistaB2C` en la lista
4. Haz clic en **Switch**

---

## Paso 3: Registrar aplicación en Azure AD B2C

### 3.1 Crear el registro

1. Busca **"Azure AD B2C"** en la barra de búsqueda
2. Selecciona **App registrations** → **New registration**
3. Completa:
   - **Name:** `transportista-backend`
   - **Supported account types:** `Accounts in any identity provider...`
   - **Redirect URI:** `https://jwt.ms` (para pruebas)
   - Clic en **Register**

### 3.2 Guardar datos importantes

Después del registro, anota estos valores (los necesitarás después):

| Valor | Dónde está | Uso |
|-------|-----------|-----|
| **Application (client) ID** | Overview | Audiencia en API Gateway |
| **Directory (tenant) ID** | Overview | Issuer URL |

---

## Paso 4: Configurar autenticación

1. En la app registrada, ve a **Authentication**
2. Marca las casillas:
   - ✅ **Access tokens** (used for implicit flows)
   - ✅ **ID tokens** (used for implicit and hybrid flows)
3. Clic en **Save**

---

## Paso 5: Configurar permisos de API

1. Ve a **API permissions** → **Add a permission**
2. Selecciona **Microsoft Graph**
3. Selecciona **Delegated permissions**
4. Busca y selecciona:
   - ✅ `openid`
   - ✅ `profile`
   - ✅ `offline_access`
5. Clic en **Add permissions**

---

## Paso 6: Crear Custom Claim (extension_consultaRole) ⭐

### 6.1 Crear atributo personalizado

1. En la página principal de Azure AD B2C, ve a **User attributes**
2. Haz clic en **Add**
3. Completa:
   - **Name:** `extension_consultaRole`
   - **Data Type:** `String`
   - **Description:** `Rol del usuario: admin o consulta`
4. Clic en **Create**

### 6.2 Configurar User Flow con el custom claim

1. Ve a **User flows** → **New user flow**
2. Selecciona **Sign up and sign in** → **Recommended** → **Create**
3. Completa:
   - **Name:** `B2C_1_signup_signin_transportista`
   - **Identity providers:** `Email signup`
4. En **User attributes** (sección 5):
   - **Collect attribute:** ✅ `extension_consultaRole`
   - **Return claim:** ✅ `extension_consultaRole`
5. Clic en **Create**

---

## Paso 7: Crear Scope para la API

1. En App registrations → `transportista-backend`, ve a **Expose an API**
2. Haz clic en **Add a scope**
3. Completa:
   - **Scope name:** `azure_aws`
   - **Admin consent display name:** `Acceso a la API transportista`
   - **Admin consent description:** `Permite acceder a los endpoints de gestión de guías`
4. Clic en **Add scope**

---

## Paso 8: Crear Client Secret

1. Ve a **Certificates & secrets** → **Client secrets** → **New client secret**
2. Completa:
   - **Description:** `secret-transportista`
   - **Expires:** 6 months
3. Clic en **Add**

⚠️ **IMPORTANTÍSIMO:** Copia el **Value** inmediatamente. No podrás verlo de nuevo después de salir de esta pantalla.

---

## Paso 9: Crear usuarios de prueba con roles

### 9.1 Ejecutar el User Flow

1. Ve a **User flows** → `B2C_1_signup_signin_transportista`
2. Haz clic en **Run user flow**
3. En la pantalla de configuración:
   - **Application:** `transportista-backend`
   - **Reply URL:** `https://jwt.ms`
4. Clic en **Run user flow**

### 9.2 Crear usuario con rol admin

1. En la pantalla de login que se abre, haz clic en **Sign up now**
2. Completa:
   - **Email:** un correo real (recibirás código de verificación)
   - **Password:** una contraseña segura
   - **extension_consultaRole:** `admin`
3. Verifica el email con el código enviado
4. Serás redirigido a `https://jwt.ms`

### 9.3 Verificar el claim

En `https://jwt.ms`, en la sección **Decoded Token → Payload**, busca:
```json
{
  "extension_consultaRole": "admin"
}
```

### 9.4 Crear usuario con rol consulta

1. Cierra la sesión actual (o abre ventana incógnito)
2. Repite el paso 9.2 pero asigna `extension_consultaRole` = `consulta`
3. Verifica en `jwt.ms` que el claim aparece como `"consulta"`

---

## Paso 10: Obtener valores para application.properties

### Issuer URI

Ejecuta el User Flow, inicia sesión con cualquier usuario, y en `jwt.ms` busca el campo `iss`:

```json
{
  "iss": "https://login.microsoftonline.com/12345678-1234-1234-1234-123456789012/v2.0/"
}
```

Asígnelo en `application.properties`:
```properties
spring.security.oauth2.resourceserver.jwt.issuer-uri=https://login.microsoftonline.com/{TENANT_ID}/v2.0/
```

### JWKS URI

Se construye automáticamente desde el issuer:
```properties
spring.security.oauth2.resourceserver.jwt.jwk-set-uri=https://login.microsoftonline.com/{TENANT_ID}/discovery/v2.0/keys
```

---

## Paso 11: Obtener token para Postman (OAuth 2.0 Client Credentials)

Cuando necesites probar endpoints securitizados con Postman:

1. En Postman, ve a la pestaña **Authorization**
2. Configura:
   - **Type:** `OAuth 2.0`
   - **Grant Type:** `Client Credentials`
   - **Access Token URL:**
     ```
     https://login.microsoftonline.com/{TENANT_NAME}.onmicrosoft.com/oauth2/v2.0/token
     ```
   - **Client ID:** el Application (client) ID de `transportista-backend`
   - **Client Secret:** el valor que guardaste en el Paso 8
   - **Scope:**
     ```
     https://{TENANT_NAME}.onmicrosoft.com/{CLIENT_ID}/.default
     ```
3. Haz clic en **Get New Access Token**
4. Haz clic en **Use Token**

---

## Paso 12: Activar seguridad en la aplicación

Una vez que Azure AD B2C esté configurado y los valores en `application.properties` completados:

```bash
# Activar el modo producción
export APP_SECURITY_ENABLED=true
./mvnw spring-boot:run
```

Ahora todos los endpoints requerirán un token JWT válido de Azure AD B2C.

---

## Verificación del Criterio

### Tests relacionados:

| Clase de test | Tests | ¿Qué valida? |
|---------------|:-----:|--------------|
| `SecurityConfigIntegrationTest` | 3 | Extracción de roles desde JWT con claim `extension_consultaRole` |
| `GuiaDespachoControllerTest$SeguridadTests` | 7 | Acceso por rol: admin/consulta/sin auth |

### Evidencia para la documentación Word:

1. Captura del tenant B2C creado en Azure Portal
2. Captura de App registrations mostrando `transportista-backend`
3. Captura de la configuración de Authentication con las casillas marcadas
4. Captura de API permissions con Microsoft Graph configurado
5. Captura del custom claim `extension_consultaRole` en User attributes
6. Captura del User Flow `B2C_1_signup_signin_transportista`
7. Captura del User Flow con el claim en "Return claims"
8. Captura de Expose an API mostrando el scope `azure_aws`
9. Captura del Client Secret creado (sin mostrar el valor)
10. Captura de `jwt.ms` mostrando el token decodificado con el claim `extension_consultaRole: "admin"`
11. Captura de `jwt.ms` mostrando el token decodificado con el claim `extension_consultaRole: "consulta"`
12. Captura de Postman obteniendo el Access Token con OAuth 2.0
13. Captura de Postman usando el token en un endpoint securitizado → 200 OK
14. Captura de Postman con token de rol incorrecto → 403 Forbidden

---

## Checklist de Verificación

- [ ] Tenant B2C `TransportistaB2C` creado
- [ ] Aplicación `transportista-backend` registrada
- [ ] Application (client) ID y Directory (tenant) ID guardados
- [ ] Access tokens e ID tokens habilitados en Authentication
- [ ] Permisos Microsoft Graph (openid, profile, offline_access) agregados
- [ ] Custom claim `extension_consultaRole` (String) creado
- [ ] User Flow `B2C_1_signup_signin_transportista` creado
- [ ] Custom claim incluido como Return claim en el User Flow
- [ ] Scope `azure_aws` expuesto en la API
- [ ] Client Secret creado y valor guardado
- [ ] Usuario con rol `admin` creado y verificado en jwt.ms
- [ ] Usuario con rol `consulta` creado y verificado en jwt.ms
- [ ] `issuer-uri` y `jwk-set-uri` configurados en application.properties
- [ ] Token obtenido exitosamente desde Postman con OAuth 2.0
- [ ] Endpoints probados con ambos roles (admin y consulta)
