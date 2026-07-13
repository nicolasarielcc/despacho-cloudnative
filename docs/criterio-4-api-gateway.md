# Criterio 4 — Integra el API Gateway con los microservicios (15 pts)

## Objetivo del Criterio

Integrar correctamente el AWS API Gateway con todos los microservicios, registrando y securitizando todos los endpoints mediante un autorizador JWT conectado a Azure AD B2C.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Todos los endpoints registrados en el API Gateway | ✅ | 7 rutas creadas (una por cada endpoint) |
| Endpoints securitizados en API Gateway | ✅ | Autorizador JWT conectado a Azure AD B2C |
| API Gateway + Azure AD B2C integrados | ✅ | JWT emitido por Azure → validado en API Gateway |
| Backend también securitizado con Spring Security | ✅ | Doble capa: API Gateway + Spring Security |
| IP elástica de EC2 como destino de las integraciones | ✅ | URI de integración apuntando a `http://{IP-EC2}:8080` |

---

## Paso 1: Acceder a AWS Academy y abrir AWS Console

1. Inicia sesión en [AWS Academy](https://aws.amazon.com/)
2. Ve a **LMS** → selecciona tu curso CDY2204
3. Ve a **Modules** → **Launch AWS Academy Learner Lab**
4. Haz clic en **Start Lab** (espera hasta que la luz cambie a verde)
5. Haz clic en el círculo verde para abrir la **AWS Console**

---

## Paso 2: Obtener credenciales de AWS

1. En el Learner Lab, haz clic en **AWS Details**
2. En la sección **AWS CLI**, copia y guarda:
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
   - `AWS_SESSION_TOKEN`

⚠️ **IMPORTANTE:** Estos valores **CAMBIAN cada vez que inicias sesión** en AWS Academy. Actualízalos en los GitHub Secrets cada vez.

---

## Paso 3: Crear el API Gateway

### 3.1 Acceder al servicio

1. En AWS Console, busca **API Gateway** en la barra de búsqueda
2. Haz clic en **API Gateway** (servicio)

### 3.2 Crear una API HTTP

1. Haz clic en **Create API**
2. Selecciona **HTTP API** (no REST API)
3. Haz clic en **Build**
4. Completa:
   - **API name:** `api-gateway-transportista`
   - Clic en **Review and Create**
   - Clic en **Create**

---

## Paso 4: Crear rutas (endpoints)

Crea una ruta por cada endpoint del microservicio:

| Ruta | Método | Integración destino |
|------|--------|---------------------|
| `/api/guias` | `POST` | `http://{IP-ELASTICA-EC2}:8080/api/guias` |
| `/api/guias/{id}` | `PUT` | `http://{IP-ELASTICA-EC2}:8080/api/guias/{id}` |
| `/api/guias/{id}` | `DELETE` | `http://{IP-ELASTICA-EC2}:8080/api/guias/{id}` |
| `/api/guias` | `GET` | `http://{IP-ELASTICA-EC2}:8080/api/guias` |
| `/api/guias/{id}/subir-s3` | `POST` | `http://{IP-ELASTICA-EC2}:8080/api/guias/{id}/subir-s3` |
| `/api/guias/{id}/descargar` | `GET` | `http://{IP-ELASTICA-EC2}:8080/api/guias/{id}/descargar` |
| `/api/cola/procesar-guias` | `POST` | `http://{IP-ELASTICA-EC2}:8080/api/cola/procesar-guias` |

### Procedimiento para cada ruta:

1. Ve a **Routes** en el panel izquierdo
2. Haz clic en **Create**
3. Selecciona el **método HTTP** (GET/POST/PUT/DELETE)
4. Escribe la **ruta** (ej: `/api/guias`)
5. Haz clic en **Create**

---

## Paso 5: Configurar integraciones

Para cada ruta, asocia una integración HTTP:

1. Selecciona la ruta creada
2. Haz clic en **Attach integration** → **Create and attach an integration**
3. Completa:
   - **Integration type:** `HTTP URI`
   - **Method:** el mismo que la ruta
   - **URI:** `http://{IP-ELASTICA-EC2}:8080/{ruta}`

**Ejemplo de URI para la ruta POST /api/guias:**
```
http://54.123.45.67:8080/api/guias
```

---

## Paso 6: Crear autorizador JWT (Azure AD B2C)

### 6.1 Configurar autorizador

1. Ve a **Authorization** en el panel izquierdo
2. Haz clic en **Create authorizer**
3. Completa:
   - **Name:** `jwt-authorizer-azure`
   - **Authorizer type:** `JWT`
   - **Issuer URL:**
     ```
     https://login.microsoftonline.com/{TENANT_ID}/v2.0/
     ```
     - Reemplaza `{TENANT_ID}` por el ID de tu tenant Azure AD B2C
     - Lo obtienes de: Azure Portal → Azure AD B2C → Overview → Tenant ID
   - **Audience:**
     ```
     {APPLICATION_CLIENT_ID}
     ```
     - Lo obtienes de: Azure Portal → App registrations → transportista-backend → Application (client) ID
4. Clic en **Create**

### 6.2 Asociar autorizador a las rutas

1. Ve a **Routes** → selecciona cada ruta
2. Haz clic en **Attach authorization**
3. Selecciona `jwt-authorizer-azure`
4. Repite para las 7 rutas

---

## Paso 7: Desplegar la API

1. Ve a **Deploy** en el panel izquierdo
2. Haz clic en **Create stage**
3. Completa:
   - **Stage name:** `produccion`
   - Clic en **Create**
4. Haz clic en **Deploy API**
5. Selecciona el stage `produccion`
6. Clic en **Deploy**

### URL base de la API:

Después del despliegue, la URL será similar a:
```
https://{api-id}.execute-api.us-east-1.amazonaws.com/produccion
```

Cópiala. Esta es la URL base que usarás en Postman para todas las pruebas.

---

## Paso 8: Probar la API securitizada

### 8.1 Sin token (debe fallar):

```bash
curl -X GET https://{api-id}.execute-api.us-east-1.amazonaws.com/produccion/api/guias
```

**Respuesta esperada:** `{"message": "Unauthorized"}` (401)

### 8.2 Con token JWT (debe funcionar):

1. Obtén un token de Azure AD B2C (ver Criterio 5, Paso 5)
2. En Postman:
   - URL: `https://{api-id}.execute-api.us-east-1.amazonaws.com/produccion/api/guias`
   - Authorization: `Bearer Token` → pega el JWT
   - Send

**Respuesta esperada:** `200 OK` con lista de guías

---

## Paso 9: Actualizar GitHub Secrets para CI/CD

Para que el pipeline de GitHub Actions funcione, configura estos secrets:

Ve a: **GitHub → tu repositorio → Settings → Secrets and variables → Actions**

| Secret | Valor | Dónde obtenerlo |
|--------|-------|-----------------|
| `AWS_ACCESS_KEY_ID` | ASIA... | AWS Academy → AWS Details |
| `AWS_SECRET_ACCESS_KEY` | abc... | AWS Academy → AWS Details |
| `AWS_SESSION_TOKEN` | IQo... | AWS Academy → AWS Details |
| `EC2_HOST` | 54.123.45.67 | IP elástica de tu máquina EC2 |
| `USER_SERVER` | ec2-user | Usuario SSH de Amazon Linux |
| `EC2_SSH_KEY` | -----BEGIN RSA... | Contenido de tu archivo .pem |
| `DOCKERHUB_USERNAME` | tuusuario | Tu cuenta de DockerHub |
| `DOCKERHUB_TOKEN` | dckr_pat... | DockerHub → Account Settings → Security |

⚠️ Recuerda: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` y `AWS_SESSION_TOKEN` **cambian cada vez** que inicias el Learner Lab.

---

## Arquitectura de seguridad (doble capa)

```
Cliente (Postman)
    │
    │  Authorization: Bearer <JWT-Azure>
    ▼
┌──────────────────────────────┐
│  AWS API Gateway             │
│  ┌─────────────────────────┐ │
│  │  jwt-authorizer-azure    │ │  ← 1ra validación JWT
│  │  (Issuer: Azure AD B2C)  │ │     ¿El JWT es válido?
│  └─────────────────────────┘ │     ¿Issuer correcto?
│              │                │     ¿Audiencia correcta?
│              ▼                │
│  ┌─────────────────────────┐ │
│  │  Ruta: /api/guias       │ │
│  │  → http://EC2:8080/...  │ │
│  └─────────────────────────┘ │
└──────────────────────────────┘
    │
    │  Reenvía el mismo JWT al backend
    ▼
┌──────────────────────────────┐
│  EC2 (Spring Boot)           │
│  ┌─────────────────────────┐ │
│  │  SecurityConfig          │ │  ← 2da validación JWT
│  │  .oauth2ResourceServer() │ │     ¿El claim extension_consultaRole
│  │  .jwt()                  │ │      tiene el rol necesario?
│  └─────────────────────────┘ │
│              │                │
│              ▼                │
│  ┌─────────────────────────┐ │
│  │  GuiaDespachoController  │ │
│  │  @PreAuthorize(admin)    │ │
│  └─────────────────────────┘ │
└──────────────────────────────┘
```

---

## Verificación del Criterio

### Tests relacionados (configuración de infraestructura Cloud):

Este criterio se valida con pruebas manuales en la nube, no con tests unitarios.

### Evidencia para la documentación Word:

1. Captura del API Gateway mostrando las 7 rutas creadas
2. Captura de cada ruta con su integración HTTP URI configurada
3. Captura del autorizador JWT con los campos Issuer URL y Audience completos
4. Captura de las rutas con el autorizador asociado (Attach authorization)
5. Captura del Stage `produccion` desplegado
6. Captura de Postman sin token → 401 Unauthorized
7. Captura de Postman con token válido (rol admin) → 200 OK en cada endpoint
8. Captura de Postman con token válido (rol consulta) → 403 en endpoints prohibidos
9. Captura de los GitHub Secrets configurados

---

## Checklist de Verificación

- [ ] AWS Academy Learner Lab iniciado (luz verde)
- [ ] API Gateway HTTP creado con nombre `api-gateway-transportista`
- [ ] 7 rutas creadas (una por cada endpoint REST)
- [ ] Integraciones HTTP URI configuradas apuntando a EC2
- [ ] Autorizador JWT creado con Issuer URL y Audience de Azure
- [ ] Autorizador asociado a las 7 rutas
- [ ] Stage `produccion` creado y desplegado
- [ ] URL de invocación obtenida y funcional
- [ ] Prueba sin token → 401
- [ ] Prueba con token válido → 200/201/204 según endpoint
- [ ] Prueba con token de rol incorrecto → 403
- [ ] GitHub Secrets actualizados para el pipeline CI/CD
- [ ] Pipeline CI/CD ejecutado exitosamente
