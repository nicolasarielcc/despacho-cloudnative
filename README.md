# Sistema de Gestión de Guías de Despacho

**Asignatura:** CDY2204 — Desarrollo Cloud Native (Duoc UC)

**Sumativa 3 — Semana 8:** Desarrollando sistema asíncrono con la utilización de colas

---

## Tabla de Contenidos

1. [Descripción](#descripción)
2. [Arquitectura](#arquitectura)
3. [Requisitos Previos](#requisitos-previos)
4. [Estructura del Proyecto](#estructura-del-proyecto)
5. [Configuración Paso a Paso](#configuración-paso-a-paso)
   - [5.1 Oracle Cloud Database](#51-oracle-cloud-database)
   - [5.2 RabbitMQ (Docker)](#52-rabbitmq-docker)
   - [5.3 Azure AD B2C](#53-azure-ad-b2c)
   - [5.4 AWS S3](#54-aws-s3)
   - [5.5 Variables de Entorno](#55-variables-de-entorno)
6. [Ejecución](#ejecución)
   - [6.1 Desarrollo Local (sin Docker)](#61-desarrollo-local-sin-docker)
   - [6.2 Con Docker Compose](#62-con-docker-compose)
7. [API Endpoints](#api-endpoints)
   - [7.1 CRUD Guías de Despacho](#71-crud-guías-de-despacho)
   - [7.2 Subida/Descarga S3](#72-subidadescarga-s3)
   - [7.3 Procesamiento Colas](#73-procesamiento-colas)
8. [Tests Automatizados — Criterios 2 y 3](#tests-automatizados--criterios-2-y-3)
   - [8.1 Ejecutar todos los tests](#81-ejecutar-todos-los-tests)
   - [8.2 Tests de RabbitMQ (Criterio 2)](#82-tests-de-rabbitmq-criterio-2)
   - [8.3 Tests de Seguridad (Criterio 3)](#83-tests-de-seguridad-criterio-3)
   - [8.4 Estructura de tests](#84-estructura-de-tests)
9. [Pruebas con Postman](#pruebas-con-postman)
   - [8.1 Modo Prueba (sin JWT)](#81-modo-prueba-sin-jwt)
   - [8.2 Modo Producción (con JWT Azure)](#82-modo-producción-con-jwt-azure)
9. [Despliegue CI/CD (GitHub Actions)](#despliegue-cicd-github-actions)
10. [Integración con API Gateway](#integración-con-api-gateway)
11. [RabbitMQ — Colas y Mensajería](#rabbitmq--colas-y-mensajería)
12. [Solución de Problemas](#solución-de-problemas)

---

## Descripción

Microservicio Spring Boot para una **empresa transportista** que gestiona pedidos y genera guías de despacho. El sistema integra:

| Componente | Rol |
|------------|-----|
| **RabbitMQ** | Mensajería asíncrona con dos colas (exitosa + error) |
| **Oracle Cloud** | Persistencia de datos (dos tablas: guías + procesadas) |
| **Azure AD B2C** | Autenticación y autorización con JWT |
| **Spring Security** | Seguridad del backend con roles (admin/consulta) |
| **AWS S3** | Almacenamiento de archivos (guías en PDF) |
| **AWS API Gateway** | Exposición securitizada de endpoints |
| **GitHub Actions** | Pipeline CI/CD automatizado |
| **Docker** | Contenerización de RabbitMQ y la app |

### Flujo del Sistema

```
POST /api/guias
     │
     ▼
[GuiaDespachoService]
     │
     ├── ✔ Éxito → GuiaProducer.enviarGuiaExitosa()
     │               └── cola-guias-exitosas → GuiaConsumer → Guarda en GUIA_DESPACHO_PROCESADA
     │
     └── ✖ Error  → GuiaProducer.enviarGuiaError()
                     └── cola-guias-error (almacenada para depuración)
```

---

## Arquitectura

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────────┐
│   Cliente   │────▶│  AWS API Gateway │────▶│ Microservicio App   │
│  (Postman)  │     │  (JWT Auth)      │     │ Spring Boot :8080   │
└─────────────┘     └──────────────────┘     └──────────┬──────────┘
                                                         │
                                    ┌────────────────────┼────────────────────┐
                                    ▼                    ▼                    ▼
                            ┌──────────────┐    ┌──────────────┐    ┌────────────────┐
                            │  RabbitMQ    │    │  Oracle BD   │    │  AWS S3        │
                            │  2 colas     │    │  2 tablas    │    │  guías .pdf    │
                            └──────────────┘    └──────────────┘    └────────────────┘
```

---

## Requisitos Previos

| Herramienta | Versión | Instalación |
|-------------|---------|-------------|
| Java JDK | 17+ | `java --version` |
| Maven | 3.8+ | `mvn --version` (o usa `./mvnw`) |
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| Cuenta AWS Academy | — | Entregada por el docente |
| Cuenta Azure | — | Suscripción activa con Azure AD B2C |
| Cuenta DockerHub | — | [hub.docker.com](https://hub.docker.com) |

---

## Estructura del Proyecto

```
transportista/
├── .env.example                 # Variables de entorno de ejemplo
├── .github/workflows/
│   └── deploy.yml               # Pipeline CI/CD GitHub Actions
├── .mvn/wrapper/                # Maven Wrapper (compilar sin Maven)
├── Dockerfile                   # Imagen multi-stage
├── docker-compose.yml           # Orquestación (RabbitMQ + App)
├── mvnw                         # Maven Wrapper (Linux/Mac)
├── pom.xml                      # Dependencias Maven
├── README.md                    # ← Este documento
└── src/
    ├── main/
    │   ├── java/com/transportista/
    │   │   ├── TransportistaApplication.java
    │   │   ├── config/
    │   │   │   ├── S3Config.java        # Config AWS S3 (placeholder)
    │   │   │   └── SecurityConfig.java  # JWT + roles admin/consulta
    │   │   ├── controller/
    │   │   │   └── GuiaDespachoController.java  # 7 endpoints REST
    │   │   ├── dto/
    │   │   │   ├── GuiaDespachoRequest.java     # DTO entrada
    │   │   │   └── GuiaDespachoResponse.java    # DTO salida
    │   │   ├── entity/
    │   │   │   ├── GuiaDespacho.java            # Tabla principal
    │   │   │   └── GuiaDespachoProcesada.java   # Tabla NUEVA
    │   │   ├── enums/
    │   │   │   └── EstadoGuia.java       # PENDIENTE/ENVIADA/CON_ERROR
    │   │   ├── rabbitmq/
    │   │   │   ├── GuiaConsumer.java     # Escucha colas 1 y 2
    │   │   │   ├── GuiaProducer.java     # Envía mensajes a colas
    │   │   │   └── RabbitMQConfig.java   # Colas, exchanges, bindings
    │   │   ├── repository/
    │   │   │   ├── GuiaDespachoRepository.java
    │   │   │   └── GuiaDespachoProcesadaRepository.java
    │   │   └── service/
    │   │       └── GuiaDespachoService.java     # Lógica de negocio
    │   └── resources/
    │       ├── application.properties    # Config principal
    │       └── application.yml           # Config YAML (S3)
    └── test/java/com/transportista/
        └── TransportistaApplicationTests.java
```

---

## Configuración Paso a Paso

### 5.1 Oracle Cloud Database

1. Ve a **Oracle Cloud → Autonomous Database**
2. Crea o selecciona una base de datos (ATP o ADW)
3. Haz clic en **DB Connection** → descarga el **wallet.zip**
4. Extrae el wallet en `transportista/src/main/resources/wallet/`
5. Obtén los valores:
   - **TNS Alias**: el nombre de tu conexión (ej: `mydb_high`)
   - **Username**: `admin` (o el que creaste)
   - **Password**: la contraseña de la base de datos
6. Edita `application.properties`:
   ```properties
   # Opción 1 — Sin wallet:
   spring.datasource.url=jdbc:oracle:thin:@<host>:1521/<service_name>
   spring.datasource.username=<username>
   spring.datasource.password=<password>

   # Opción 2 — Con wallet (recomendado):
   # spring.datasource.url=jdbc:oracle:thin:@<TNS_ALIAS>?TNS_ADMIN=src/main/resources/wallet
   ```

### 5.2 RabbitMQ (Docker)

```bash
# Opción 1: Solo RabbitMQ
docker run -d \
  --name rabbitmq-transportista \
  -p 5672:5672 \
  -p 15672:15672 \
  rabbitmq:3-management

# Opción 2: Con Docker Compose (incluye la app, descomentarla primero)
cd transportista/
docker compose up -d rabbitmq
```

Verificar: http://localhost:15672 (guest/guest)

**Crear colas y exchange en la UI:**

1. Ve a **Queues** → **Add a new queue**
   - Name: `cola-guias-exitosas` → Add queue
   - Name: `cola-guias-error` → Add queue

2. Ve a **Exchanges** → **Add a new exchange**
   - Name: `exchange-guias` → Type: `direct` → Add exchange

3. En el exchange `exchange-guias`, ve a **Bindings**:
   - Queue: `cola-guias-exitosas` → Routing key: `guia.exitosa` → Bind
   - Queue: `cola-guias-error` → Routing key: `guia.error` → Bind

### 5.3 Azure AD B2C

Sigue los pasos detallados de la **Guía Semana 4** para:

1. **Crear Tenant B2C** en Azure Portal
2. **Registrar aplicación** `transportista-backend`
3. **Crear User Flow** `B2C_1_signup_signin_transportista` con:
   - Atributos de registro: `extension_consultaRole`
   - Return claims: `extension_consultaRole`
4. **Agregar custom claim** `extension_consultaRole` (tipo String)
5. **Exponer un Scope** llamado `azure_aws`
6. **Crear Client Secret**
7. **Crear 2 usuarios de prueba**:
   - Usuario 1 → `extension_consultaRole` = `consulta`
   - Usuario 2 → `extension_consultaRole` = `admin`

**Obtener valores para application.properties:**

| Valor | Dónde obtenerlo |
|-------|-----------------|
| `issuer-uri` | `User Flows → Run user flow → jwt.ms → campo "iss"` |
| `jwk-set-uri` | `{issuer}/discovery/v2.0/keys` |
| `Client ID` | `App registrations → transportista-backend → Application (client) ID` |
| `Tenant ID` | `App registrations → transportista-backend → Directory (tenant) ID` |

### 5.4 AWS S3

1. Ve a **AWS Console → S3 → Create bucket**
2. Nombre: `guias-despacho-transportista-<tus-iniciales>` (único global)
3. Desbloquear acceso público
4. Crear estructura de carpetas (opcional, se crean automáticamente):
   ```
   guias/{transportista}/{año}/{mes}/guia-{codigo}.pdf
   ```

**Activar S3 en el código:**

1. Edita `S3Config.java` y descomenta el código (elimina `/*` y `*/`)
2. Edita `application.yml` y descomenta la sección `cloud.aws`
3. Implementa la subida real en `GuiaDespachoService.subirGuiaAS3()` donde dice `TODO`

### 5.5 Variables de Entorno

Copia `.env.example` como `.env` y completa:

```bash
cp .env.example .env
# Edita .env con tus valores reales
```

O expórtalas directamente:

```bash
export ORACLE_URL=jdbc:oracle:thin:@...
export ORACLE_USERNAME=admin
export ORACLE_PASSWORD=TuPassword123
export RABBITMQ_HOST=localhost
export APP_SECURITY_ENABLED=false
```

---

## Ejecución

### 6.1 Desarrollo Local (sin Docker)

```bash
# 1. Asegúrate de tener RabbitMQ corriendo
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 2. Compilar y ejecutar
cd transportista/
./mvnw spring-boot:run

# Opcional: pasar variables de entorno
ORACLE_USERNAME=admin ORACLE_PASSWORD=pass ./mvnw spring-boot:run
```

La app arranca en: **http://localhost:8080**

Health check: http://localhost:8080/actuator/health

### 6.2 Con Docker Compose

```bash
# 1. Edita docker-compose.yml y descomenta la sección "app:"
# 2. Ajusta las variables de entorno en el mismo archivo
# 3. Ejecuta:
cd transportista/
docker compose up -d --build
```

---

## API Endpoints

### 7.1 CRUD Guías de Despacho

#### `POST /api/guias` — Crear guía

**Rol requerido:** admin

```json
{
  "transportista": "Juan Perez",
  "fechaEmision": "2025-01-15T10:30:00",
  "origen": "Santiago",
  "destino": "Valparaíso",
  "descripcionCarga": "Electrodomésticos - 3 cajas",
  "pesoKg": 150.5
}
```

**Respuesta:** `201 Created`

```json
{
  "id": 1,
  "codigoGuia": "GD-202501151030-042",
  "transportista": "Juan Perez",
  "fechaEmision": "2025-01-15T10:30:00",
  "origen": "Santiago",
  "destino": "Valparaíso",
  "descripcionCarga": "Electrodomésticos - 3 cajas",
  "pesoKg": 150.5,
  "estado": "ENVIADA",
  "urlS3": null,
  "fechaCreacion": "2025-01-15T10:30:05"
}
```

---

#### `PUT /api/guias/{id}` — Modificar guía

**Rol requerido:** admin

Mismos campos que POST. Se reenvía automáticamente a la cola RabbitMQ.

**Respuesta:** `200 OK`

---

#### `DELETE /api/guias/{id}` — Eliminar guía

**Rol requerido:** admin

**Respuesta:** `204 No Content`

---

#### `GET /api/guias?transportista=Juan Perez&fecha=2025-01-15` — Consultar

**Rol requerido:** admin

| Parámetro | Tipo | Obligatorio | Descripción |
|-----------|------|-------------|-------------|
| `transportista` | string | No | Filtrar por nombre |
| `fecha` | string | No | Filtrar por fecha (yyyy-MM-dd) |

**Ejemplos:**
```
GET /api/guias?transportista=Juan%20Perez&fecha=2025-01-15
GET /api/guias?transportista=Juan%20Perez
GET /api/guias?fecha=2025-01-15
GET /api/guias
```

**Respuesta:** `200 OK` — Lista de guías

---

### 7.2 Subida/Descarga S3

#### `POST /api/guias/{id}/subir-s3` — Subir a S3

**Rol requerido:** admin

Genera la URL de S3 y la guarda en la guía. La estructura de carpetas es:
```
guias/{transportista}/{año}/{mes}/guia-{codigo}.pdf
```

**Respuesta:** `200 OK` — Guía con campo `urlS3` completado

---

#### `GET /api/guias/{id}/descargar` — Descargar guía

**Rol requerido:** consulta (SOLO este rol)

Único endpoint que puede usar el rol `consulta`. Devuelve los datos de la guía para descarga.

**Respuesta:** `200 OK`

---

### 7.3 Procesamiento Colas

#### `POST /api/cola/procesar-guias` — Procesar cola 1

**Rol requerido:** admin

Consume todas las guías de `cola-guias-exitosas` y las guarda en la tabla **GUIA_DESPACHO_PROCESADA** (tabla NUEVA, distinta a las sumativas anteriores).

**Respuesta:** `200 OK` — Lista de guías procesadas

---

## Tests Automatizados — Criterios 2 y 3

Los tests cubren completamente los criterios de evaluación **2 (RabbitMQ)** y **3 (Spring Security)** y se ejecutan sin necesidad de servicios externos (usan H2 en memoria y mocks).

### 8.1 Ejecutar todos los tests

```bash
# Desde la carpeta del proyecto
cd transportista/

# Ejecutar todos los tests (perfil test con H2)
./mvnw test

# En WSL (Windows Subsystem for Linux) usar forkCount=0:
./mvnw test -DforkCount=0

# Ejecutar tests de una clase específica
./mvnw test -Dtest=GuiaProducerTest -DforkCount=0
./mvnw test -Dtest=GuiaDespachoControllerTest -DforkCount=0

# Ejecutar sin tests (solo compilar)
./mvnw clean package -DskipTests
```

**Salida esperada:**

```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.transportista.rabbitmq.GuiaProducerTest
[INFO] Tests run: 5, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.transportista.rabbitmq.GuiaConsumerTest
[INFO] Tests run: 6, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.transportista.service.GuiaDespachoServiceTest
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.transportista.controller.GuiaDespachoControllerTest
[INFO] Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.transportista.config.SecurityConfigIntegrationTest
[INFO] Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.transportista.TransportistaApplicationTests
[INFO] Tests run: 11, Failures: 0, Errors: 0, Skipped: 0
[INFO] -------------------------------------------------------
[INFO] BUILD SUCCESS
```

### 8.2 Tests de RabbitMQ (Criterio 2)

#### `GuiaProducerTest` — Envío de mensajes (5 tests)

Verifica que el productor envía correctamente a las colas:

| Test | Qué valida |
|------|-----------|
| `enviarGuiaExitosa` usa exchange y routing key correctos | `exchange-guias` + `guia.exitosa` |
| `enviarGuiaExitosa` envía el mismo objeto GuiaDespacho | Serialización correcta a JSON |
| `enviarGuiaError` usa exchange y routing key correctos | `exchange-guias` + `guia.error` |
| `enviarGuiaError` envía guía en estado CON_ERROR | Mensajes de error también se transmiten |
| No se mezclan routing keys | Exitosa nunca usa `guia.error` y viceversa |

#### `GuiaConsumerTest` — Consumo de mensajes (6 tests)

Verifica el procesamiento de mensajes recibidos:

| Test | Qué valida |
|------|-----------|
| `consumirGuiaExitosa` guarda en tabla procesada | Transformación a GuiaDespachoProcesada |
| Asigna fechaProcesamiento automáticamente | Campo específico de la tabla NUEVA |
| Si falla DB → lanza RuntimeException | Activa DLX para reintentos |
| `consumirGuiaError` NO guarda en BD | Solo log, sin persistencia |
| `consumirGuiaError` no lanza excepción | El mensaje queda en la cola |
| Conserva URL S3 del mensaje original | Integridad de datos |

#### Tests de contexto — Beans RabbitMQ (7 tests en `TransportistaApplicationTests`)

| Test | Qué valida |
|------|-----------|
| `colExitosa` está configurada | Bean Queue creado |
| `colaError` está configurada | Bean Queue con DLX creado |
| `exchange` está configurado (direct) | Bean DirectExchange creado |
| Binding exitosa existe | `cola-exitosa` ← `exchange-guias` con `guia.exitosa` |
| Binding error existe | `cola-error` ← `exchange-guias` con `guia.error` |
| Routing keys son diferentes | `guia.exitosa` ≠ `guia.error` |
| Colas son diferentes | `cola-guias-exitosas` ≠ `cola-guias-error` |

### 8.3 Tests de Seguridad (Criterio 3)

#### `GuiaDespachoControllerTest` — Control de acceso (17 tests)

Usa `@WithMockUser` para simular usuarios autenticados con roles:

| Categoría | Tests | Qué valida |
|-----------|-------|-----------|
| Sin autenticación | 1 | 401 Unauthorized sin token |
| Rol `consulta` | 6 | Puede descargar guías (200) pero NO crear, modificar, eliminar, consultar, subir, procesar (403) |
| Rol `admin` | 2 | Puede crear guías (201) pero NO puede descargar (403) |
| CRUD funcional | 5 | POST→201, PUT→200, DELETE→204, GET→200 con y sin filtros |
| Validación | 1 | POST sin campos obligatorios → 400 Bad Request |

**Matriz de acceso por rol:**

| Endpoint | `admin` | `consulta` | Sin auth |
|----------|:-------:|:----------:|:--------:|
| `POST /api/guias` | ✅ 201 | ❌ 403 | ❌ 401 |
| `PUT /api/guias/{id}` | ✅ 200 | ❌ 403 | ❌ 401 |
| `DELETE /api/guias/{id}` | ✅ 204 | ❌ 403 | ❌ 401 |
| `GET /api/guias` | ✅ 200 | ❌ 403 | ❌ 401 |
| `POST /api/guias/{id}/subir-s3` | ✅ 200 | ❌ 403 | ❌ 401 |
| `GET /api/guias/{id}/descargar` | ❌ 403 | ✅ 200 | ❌ 401 |
| `POST /api/cola/procesar-guias` | ✅ 200 | ❌ 403 | ❌ 401 |

#### `SecurityConfigIntegrationTest` — Configuración de seguridad (4 tests)

| Test | Qué valida |
|------|-----------|
| Contexto carga correctamente | Spring Boot + perfil test + H2 |
| JWT con claim `admin` → autoridad admin | Extracción correcta de roles del claim |
| JWT con claim `consulta` → autoridad consulta | Extracción correcta de roles del claim |
| JWT sin claim → sin autoridades | Seguridad por defecto: denegar acceso |

### 8.4 Estructura de tests

```
src/test/java/com/transportista/
├── TransportistaApplicationTests.java        # Tests de contexto (11 tests)
├── rabbitmq/
│   ├── GuiaProducerTest.java                  # Productor RabbitMQ (5 tests)
│   └── GuiaConsumerTest.java                 # Consumidor RabbitMQ (6 tests)
├── service/
│   └── GuiaDespachoServiceTest.java           # Lógica de negocio (11 tests)
├── controller/
│   └── GuiaDespachoControllerTest.java        # Endpoints + seguridad (17 tests)
└── config/
    └── SecurityConfigIntegrationTest.java      # Config de seguridad (4 tests)
```

**Total: 6 clases, 54 tests unitarios y de integración.**

---

## Perfil de Desarrollo Local (dev)

Para ejecutar localmente sin Oracle Cloud:

```bash
# Con perfil dev (H2 en memoria + seguridad desactivada)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Acceder a la consola H2
# http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:transportistadb
# User: sa / Password: (vacío)
```

El perfil `dev` configura automáticamente:
- H2 en memoria (sin Oracle)
- Seguridad desactivada (`app.security.enabled=false`)
- Consola H2 habilitada para debug
- RabbitMQ apuntando a `localhost`

**Requisito para desarrollo local con dev:** solo necesitas RabbitMQ corriendo en Docker.

```bash
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management
```

---

## Pruebas con Postman

### 9.1 Modo Prueba (sin JWT)

Con `app.security.enabled=false`, cualquier petición funciona sin token:

```
GET  http://localhost:8080/api/guias
POST http://localhost:8080/api/guias  (con body JSON)
```

### 9.2 Modo Producción (con JWT Azure)

Con `app.security.enabled=true`, necesitas un token JWT de Azure AD B2C:

1. Ejecuta el **User Flow** en Azure Portal
2. Inicia sesión con un usuario creado
3. Serás redirigido a `https://jwt.ms`
4. Copia el token JWT completo
5. En Postman, ve a la pestaña **Authorization**:
   - Type: `Bearer Token`
   - Token: pega el JWT copiado
6. Ahora puedes llamar a los endpoints protegidos

---

## Despliegue CI/CD (GitHub Actions)

El pipeline en `.github/workflows/deploy.yml` se gatilla con cada `push` a `main`.

### Configurar Secrets en GitHub

Ve a: **GitHub → Repositorio → Settings → Secrets and variables → Actions**

| Secret | Valor |
|--------|-------|
| `DOCKERHUB_USERNAME` | Tu usuario de DockerHub |
| `DOCKERHUB_TOKEN` | Token de acceso (Account Settings → Security) |
| `EC2_HOST` | IP elástica de tu EC2 |
| `USER_SERVER` | `ec2-user` (para Amazon Linux) |
| `EC2_SSH_KEY` | Contenido completo del archivo `.pem` (incluir saltos de línea como `\n`) |
| `AWS_ACCESS_KEY_ID` | De AWS Academy → AWS Details |
| `AWS_SECRET_ACCESS_KEY` | De AWS Academy → AWS Details |
| `AWS_SESSION_TOKEN` | De AWS Academy → AWS Details |

---

## Integración con API Gateway

1. **Crear API HTTP** en AWS API Gateway
2. **Crear rutas** para cada endpoint (GET, POST, PUT, DELETE)
3. **Configurar integraciones** apuntando a `http://{IP-EC2}:8080/{endpoint}`
4. **Crear autorizador JWT**:
   - Emisor: `https://login.microsoftonline.com/{TENANT_ID}/v2.0/`
   - Audiencia: `{Client ID de Azure AD B2C}`
5. **Asociar autorizador** a todas las rutas
6. **Desplegar** en un Stage

---

## RabbitMQ — Colas y Mensajería

### Estructura

```
Exchange: exchange-guias (direct)
  ├── routing key "guia.exitosa" → cola-guias-exitosas
  └── routing key "guia.error"   → cola-guias-error
```

### Crear colas en la UI de RabbitMQ

1. Ir a http://localhost:15672 (guest/guest)
2. **Queues → Add a new queue**:
   - `cola-guias-exitosas` (durable)
   - `cola-guias-error` (durable)
3. **Exchanges → Add a new exchange**:
   - `exchange-guias` (type: direct)
4. En `exchange-guias` → **Bindings**:
   - Bind `cola-guias-exitosas` con routing key `guia.exitosa`
   - Bind `cola-guias-error` con routing key `guia.error`

### Monitoreo

```
Interfaz web:   http://localhost:15672
Health check:   GET http://localhost:8080/actuator/health
Métricas:       GET http://localhost:8080/actuator/metrics
```

---

## Solución de Problemas

| Problema | Causa | Solución |
|----------|-------|----------|
| `ORA-XXXXX` al iniciar | Oracle no accesible o credenciales incorrectas | Verificar `application.properties` y wallet |
| `Connection refused (localhost:5672)` | RabbitMQ no está corriendo | `docker start rabbitmq-transportista` |
| `401 Unauthorized` en endpoints | `app.security.enabled=true` sin token JWT | Usar `false` en desarrollo o agregar Bearer Token |
| `403 Forbidden` | Token no tiene el rol adecuado | Verificar el claim `extension_consultaRole` en jwt.ms |
| Pipeline falla en GitHub | Secrets desactualizados | Actualizar `AWS_*` desde AWS Academy |
| No se crean las tablas automáticamente | URL de BD incorrecta o falta wallet | Verificar `spring.jpa.hibernate.ddl-auto=update` |
| Los mensajes de RabbitMQ no llegan | Colas/exchange no creados manualmente | Crearlos en la UI (http://localhost:15672) |
| `NoClassDefFoundError` | Falta dependencia Maven | `./mvnw clean install -DskipTests` |

---

## Checklist de Entrega (Sumativa 3)

- [ ] **Código**: repositorio GitHub con el proyecto completo
- [ ] **Documentación Word**: paso a paso de configuración API Gateway + Azure AD B2C
- [ ] **Evidencias Postman**: capturas de pantalla de cada endpoint funcionando
- [ ] **Video** (5-10 min): presentación en Teams mostrando el sistema funcionando
- [ ] **Roles configurados**: usuario `consulta` y usuario `admin` en Azure AD B2C
- [ ] **Colas RabbitMQ**: mensajes fluyendo de cola-exitosa a tabla procesada
- [ ] **Docker**: RabbitMQ y app contenerizados
- [ ] **S3**: guías subidas con estructura de carpetas organizada
- [ ] **API Gateway**: todos los endpoints registrados y securitizados
