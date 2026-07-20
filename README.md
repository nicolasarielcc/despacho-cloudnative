# Plataforma de Gestión de Cursos en Línea

**Asignatura:** CDY2204 — Desarrollo Cloud Native (Duoc UC)
**Evaluación Final Transversal — Semana 9**

Plataforma Cloud Native para gestión de cursos en línea donde estudiantes pueden inscribirse, acceder al contenido y recibir calificaciones, e instructores gestionan cursos en tiempo real.

| Componente | Tecnología |
|------------|-----------|
| Backend | Spring Boot 3.2 + Java 17 |
| Mensajería | RabbitMQ (Docker) — colas de inscripciones + DLQ |
| IDaaS | Azure AD B2C — roles instructor / estudiante |
| Almacenamiento | AWS S3 — material de cursos (PDF) |
| API Manager | AWS API Gateway + Lambda Authorizer JWT |
| Base de datos | Oracle Cloud (ATP) |
| CI/CD | GitHub Actions → Docker Hub → AWS EC2 |

---

## Arquitectura

```
┌──────────┐     ┌──────────────────┐     ┌────────────────────────┐
│  Cliente │────▶│  AWS API Gateway │────▶│  Microservicio App     │
│ (Postman)│     │  (JWT Authorizer)│     │  Spring Boot :8080     │
└──────────┘     └──────────────────┘     └───────────┬────────────┘
                                                       │
                  ┌────────────────────────────────────┼────────────────────┐
                  ▼                                    ▼                    ▼
          ┌──────────────┐    ┌──────────────────┐    ┌──────────────────┐
          │  RabbitMQ    │    │  Oracle Cloud    │    │  AWS S3          │
          │  2 colas+DLQ │    │  CURSO+INSCRIP   │    │  cursos/*.pdf    │
          └──────────────┘    └──────────────────┘    └──────────────────┘
```

**Flujo de inscripción:**
```
POST /api/inscripciones (estudiante)
  → guarda en INSCRIPCION (estado PENDIENTE)
  → envía a exchange-cursos (routing: curso.nueva)
  → CursoConsumer recibe de cola-cursos-principal
  → genera comprobante PDF, sube a S3
  → actualiza estado a INSCRITO
  ── si falla ──→ DLX → cola-cursos-dlq → marca CON_ERROR
```

---

## Requisitos Previos

| Herramienta | Versión | Verificación |
|-------------|---------|-------------|
| Java JDK | 17+ | `java --version` |
| Maven | 3.8+ | `mvn --version` o `./mvnw` |
| Docker | 24+ | `docker --version` |
| Docker Compose | 2.20+ | `docker compose version` |
| AWS Academy | Learner Lab | Entregado por docente |
| Azure | AD B2C Tenant | `despachoservice2.onmicrosoft.com` |
| DockerHub | Cuenta | `hub.docker.com` |

---

## Estructura del Proyecto

```
transportista/
├── .env                          # Variables de entorno (no se sube a git)
├── .env.example                  # Plantilla de variables
├── .github/workflows/deploy.yml  # Pipeline CI/CD
├── Dockerfile                    # Imagen multi-stage
├── docker-compose.yml            # RabbitMQ cluster + app (producer/consumer)
├── pom.xml                       # Dependencias Maven
├── src/main/java/com/transportista/
│   ├── TransportistaApplication.java
│   ├── config/
│   │   ├── S3Config.java            # Cliente AWS S3
│   │   └── SecurityConfig.java      # JWT + roles instructor/estudiante
│   ├── controller/
│   │   ├── CursoController.java     # 8 endpoints (CRUD cursos + cola)
│   │   └── InscripcionController.java # 3 endpoints (inscripción + calificar)
│   ├── dto/
│   │   ├── CursoRequest.java / CursoResponse.java
│   │   └── InscripcionRequest.java / InscripcionResponse.java
│   ├── entity/
│   │   ├── Curso.java               # Tabla CURSO
│   │   └── Inscripcion.java         # Tabla INSCRIPCION
│   ├── enums/
│   │   ├── EstadoCurso.java         # PENDIENTE | PUBLICADO | CERRADO
│   │   └── EstadoInscripcion.java   # PENDIENTE | INSCRITO | CALIFICADO | CON_ERROR
│   ├── rabbitmq/
│   │   ├── RabbitMQConfig.java      # Colas, exchange, bindings, DLX
│   │   ├── CursoProducer.java       # Envío a colas + pull HTTP
│   │   └── CursoConsumer.java       # @RabbitListener + DLQ handler
│   ├── repository/
│   │   ├── CursoRepository.java
│   │   └── InscripcionRepository.java
│   ├── service/
│   │   ├── CursoService.java        # Lógica de cursos
│   │   ├── InscripcionService.java   # Lógica de inscripciones
│   │   └── S3Service.java           # Upload/download a S3
│   └── resources/
│       ├── application.properties
│       ├── application.yml
│       ├── application-dev.properties
│       └── db/schema.sql            # DDL Oracle
└── src/test/java/com/transportista/
    ├── TransportistaApplicationTests.java (11 tests)
    ├── config/
    │   ├── SecurityConfigIntegrationTest.java (4 tests)
    │   └── TestS3Config.java
    ├── controller/
    │   └── CursoControllerTest.java (10 tests)
    ├── rabbitmq/
    │   ├── CursoProducerTest.java (4 tests)
    │   └── CursoConsumerTest.java (3 tests)
    └── service/
        ├── CursoServiceTest.java (9 tests)
        └── InscripcionServiceTest.java (4 tests)
```

---

## API Endpoints

### Cursos (instructor)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/cursos` | Crear curso |
| `PUT` | `/api/cursos/{id}` | Modificar curso |
| `DELETE` | `/api/cursos/{id}` | Eliminar curso |
| `GET` | `/api/cursos?instructor=&fecha=` | Consultar cursos |
| `POST` | `/api/cursos/{id}/subir-s3` | Subir material a S3 |

### Cursos (estudiante)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/cursos/{id}/descargar` | Descargar material |

### Inscripciones (estudiante)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/inscripciones` | Inscribirse a curso |
| `GET` | `/api/inscripciones?estudiante=` | Consultar mis inscripciones |

### Inscripciones (instructor)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `PUT` | `/api/inscripciones/{id}/calificar?calificacion=6.5` | Asignar calificación |

### Colas (instructor)

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/cola/consumir-mensaje` | Consumir mensaje de cola (HTTP pull) |
| `POST` | `/api/cola/procesar-inscripciones` | Procesar inscripciones enviadas |

### Ejemplos de requests

**Crear curso:**
```json
POST /api/cursos
{
  "nombre": "Curso de Java Avanzado",
  "instructor": "Juan Perez",
  "descripcion": "Programación orientada a objetos, streams, concurrencia",
  "creditos": 5.0,
  "fechaInicio": "2025-03-01T09:00:00",
  "fechaFin": "2025-07-15T18:00:00"
}
```

**Inscribirse:**
```json
POST /api/inscripciones
{
  "codigoCurso": "CUR-202503010900-042",
  "estudiante": "Maria Lopez",
  "emailEstudiante": "maria.lopez@email.com"
}
```

---

## RabbitMQ — Colas y Mensajería

### Configuración

```
Exchange: exchange-cursos (direct)
  ├── routing key "curso.nueva"  → cola-cursos-principal (con DLX)
  └── DLX: dlx-exchange          → cola-cursos-dlq
```

### Levantar con Docker

```bash
# Opción 1: Solo RabbitMQ
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# Opción 2: Cluster + App (docker-compose)
docker compose up -d
```

UI de administración: http://localhost:15672 (guest/guest)

---

## Configuración

### 1. Variables de entorno (.env)

```bash
cp .env.example .env
# Editar .env con valores reales
```

### 2. Oracle Cloud

```properties
# application.properties
spring.datasource.url=${ORACLE_URL:jdbc:oracle:thin:@localhost:1521/XEPDB1}
spring.datasource.username=${ORACLE_USERNAME:admin}
spring.datasource.password=${ORACLE_PASSWORD}
```

O usar wallet: descomentar líneas con `TNS_ADMIN` en `application.properties`.

### 3. Azure AD B2C

| Config | Valor |
|--------|-------|
| Tenant | `despachoservice2.onmicrosoft.com` |
| Tenant ID | `5199d2b5-40ed-44c1-a8e5-f4a83132a743` |
| App | `despacho-service-api2` |
| Client ID | `0a27f262-a186-457f-b7b5-eb43b284cd4c` |
| User Flow | `B2C_1_despacho_signin` |
| Usuario instructor | `nikocarambas@gmail.com` (admin) |
| Usuario estudiante | `ni.cavieres@duocuc.cl` (consulta) |

### 4. AWS S3

```yaml
# application.yml
cloud:
  aws:
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}
      session-token: ${AWS_SESSION_TOKEN}
    region:
      static: ${AWS_REGION:us-east-1}
    s3:
      bucket-name: ${S3_BUCKET_NAME:cursos-grupo3-bucket}
```

Estructura en S3: `cursos/{nombre_curso}/{año}/{mes}/guia-{codigoCurso}.pdf`

---

## Ejecución

### Desarrollo local (perfil dev, H2 en memoria)

```bash
# 1. RabbitMQ
docker run -d -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 2. Aplicación
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

- App: http://localhost:8080
- H2 Console: http://localhost:8080/h2-console (jdbc:h2:mem:transportistadb, sa/)
- RabbitMQ UI: http://localhost:15672

### Con Docker Compose (RabbitMQ cluster + app)

```bash
docker compose up -d --build
```

Esto levanta 4 contenedores:
- `rabbitmq1` (puerto 15672)
- `rabbitmq2` (puerto 15673) — clustering
- `cursosonline-producer` (puerto 8080) — listeners desactivados
- `cursosonline-consumer` (puerto 8081) — escucha colas

---

## CI/CD Pipeline

El pipeline en `.github/workflows/deploy.yml`:

1. **Push a main** → se gatilla
2. Genera `application.yml` con credenciales AWS
3. Login a DockerHub
4. Build + Push de imagen `cursosonline-app:latest`
5. SSH a EC2 → pull de imagen → stop/rm contenedores viejos → run producer + consumer
6. Health check en `:8080/actuator/health`

### GitHub Secrets requeridos

| Secret | Descripción |
|--------|-------------|
| `DOCKERHUB_USERNAME` | Usuario Docker Hub |
| `DOCKERHUB_TOKEN` | Token de acceso Docker Hub |
| `EC2_HOST` | IP elástica de EC2 |
| `EC2_SSH_KEY` | Llave privada .pem |
| `USER_SERVER` | `ec2-user` |
| `AWS_ACCESS_KEY_ID` | AWS Academy → AWS Details |
| `AWS_SECRET_ACCESS_KEY` | AWS Academy → AWS Details |
| `AWS_SESSION_TOKEN` | AWS Academy → AWS Details |
| `S3_BUCKET_NAME` | Variable de GitHub (no secret) |

---

## Tests

```bash
# Todos los tests (45 tests, 0 fallos)
./mvnw test

# Test específico
./mvnw test -Dtest=CursoServiceTest
./mvnw test -Dtest=CursoControllerTest
./mvnw test -Dtest=CursoProducerTest
```

### Cobertura de tests

| Clase | Tests | Tipo |
|-------|:-----:|------|
| `TransportistaApplicationTests` | 11 | Contexto + beans RabbitMQ + seguridad |
| `CursoServiceTest` | 9 | Lógica de negocio cursos |
| `InscripcionServiceTest` | 4 | Lógica de inscripciones |
| `CursoControllerTest` | 10 | Endpoints REST |
| `CursoProducerTest` | 4 | Envío a colas RabbitMQ |
| `CursoConsumerTest` | 3 | Consumo de colas + DLQ |
| `SecurityConfigIntegrationTest` | 4 | Roles JWT (admin→instructor, consulta→estudiante) |
| **Total** | **45** | 0 fallos |

---

## API Gateway + Lambda Authorizer

El proyecto incluye un autorizador Lambda en `lambda-authorizer/authorizer.py` que:
- Valida tokens JWT contra Azure AD B2C (JWKS)
- Verifica issuer, audiencia y expiración
- Extrae `extension_consultaRole` del token
- Retorna `isAuthorized: true/false` al API Gateway

Para desplegar: comprimir `authorizer.py` + dependencias en `authorizer.zip` y subir a AWS Lambda.

---

## Solución de Problemas

| Problema | Causa | Solución |
|----------|-------|----------|
| `Connection refused: 5672` | RabbitMQ no iniciado | `docker start rabbitmq` |
| `401 Unauthorized` | `app.security.enabled=true` sin JWT | Usar `false` o enviar Bearer token |
| `403 Forbidden` | Rol incorrecto en JWT | Verificar `extension_consultaRole` en jwt.ms |
| Pipeline falla | Secrets desactualizados | Actualizar `AWS_*` desde AWS Academy |
| No se crean tablas | URL BD incorrecta | Verificar `ddl-auto=update` y conexión Oracle |
| `S3Exception` | Credenciales AWS expiradas | Regenerar desde AWS Academy → AWS Details |

---

## Entrega EFT

- [x] Código en GitHub con microservicios Spring Boot
- [x] Colas RabbitMQ desplegadas en Docker
- [x] Productor y consumidor en Java
- [x] IDaaS Azure AD B2C con roles instructor/estudiante
- [x] Spring Security con validación JWT
- [x] Almacenamiento S3 para material de cursos
- [x] CI/CD con GitHub Actions → EC2
- [x] Lambda Authorizer para API Gateway
- [x] 45 tests automatizados
- [ ] Documentación Word (paso a paso IDaaS + API Manager + Colas)
- [ ] Video presentación (5-10 min)
- [ ] Evidencias Postman
