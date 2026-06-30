# despacho-service

**CDY2204 – Desarrollo Cloud Native | Semana 6**

Microservicio REST desarrollado con **Spring Boot 3** para la gestión de guías de despacho de una empresa transportista. Las guías se generan como archivos PDF, se almacenan temporalmente en **AWS EFS** y luego se publican de forma organizada en **AWS S3**. Todos los endpoints están protegidos mediante **Azure AD B2C** como proveedor de identidad (IDaaS), integrado con **AWS API Gateway**.

---

## Tabla de contenidos

1. [Tecnologías utilizadas](#tecnologías-utilizadas)
2. [Arquitectura de almacenamiento](#arquitectura-de-almacenamiento)
3. [Arquitectura de seguridad](#arquitectura-de-seguridad)
4. [Estructura del proyecto](#estructura-del-proyecto)
5. [Variables de entorno](#variables-de-entorno)
6. [Ejecución local](#ejecución-local)
7. [Endpoints disponibles](#endpoints-disponibles)
8. [Roles y permisos](#roles-y-permisos)
9. [Configuración de AWS EFS en EC2](#configuración-de-aws-efs-en-ec2)
10. [Pipeline CI/CD](#pipeline-cicd)
11. [GitHub Secrets requeridos](#github-secrets-requeridos)

---

## Tecnologías utilizadas

| Componente | Tecnología |
|---|---|
| Lenguaje | Java 17 |
| Framework | Spring Boot 3.5 |
| Seguridad | Spring Security + OAuth2 Resource Server |
| IDaaS | Azure Active Directory B2C |
| API Manager | AWS API Gateway |
| Persistencia | Spring Data JPA + H2 (en memoria) |
| Generación de PDF | Apache PDFBox 3.0.2 |
| Almacenamiento cloud | AWS SDK v2 — S3 |
| Almacenamiento temporal | AWS EFS (montado como volumen Docker) |
| Contenedor | Docker — imagen `eclipse-temurin:17-jre-alpine` |
| CI/CD | GitHub Actions → Docker Hub → EC2 |

---

## Arquitectura de almacenamiento

El flujo de vida de una guía pasa por dos etapas de almacenamiento:

```
POST /api/guias
       │
       ▼
  Genera PDF con PDFBox
       │
       ▼
[EFS] /tmp/guias/guia-{id}-{numero}.pdf     ← almacenamiento temporal
       │
       ▼  POST /api/guias/{id}/subir
       │
       ▼
[S3] /{yyyy}/{MM}/{dd}/{transportista}/guia-{numero}.pdf   ← almacenamiento definitivo
```

La organización en S3 sigue el patrón: `año/mes/día/transportista/guia-{numero}.pdf`

Ejemplo real: `2026/06/08/transportes-del-sur/guia-g-001.pdf`

---

## Arquitectura de seguridad

```
Cliente (Postman / Frontend)
       │
       ▼
  Azure AD B2C  ──→  emite token JWT con custom claim extension_rolDespacho
       │
       ▼
  AWS API Gateway  ──→  valida el token y enruta la petición
       │
       ▼
  despacho-service (Spring Boot)
       │
       ▼
  OAuth2 Resource Server  ──→  verifica firma JWT via jwks_uri de Azure
       │
       ▼
  SecurityConfig  ──→  aplica autorización por custom claim (descarga | gestion)
```

El backend **no gestiona usuarios ni genera tokens propios**. Toda la autenticación es delegada a Azure AD B2C. Spring Boot actúa únicamente como Resource Server: valida el JWT entrante y aplica las reglas de autorización según el custom claim `extension_rolDespacho`.

---

## Estructura del proyecto

```
despacho-service/
├── .github/
│   └── workflows/
│       └── deploy.yaml                    # Pipeline CI/CD
├── src/
│   └── main/
│       ├── java/com/duoc/despacho_service/
│       │   ├── config/
│       │   │   ├── AwsS3Config.java            # Bean S3Client con credenciales IAM
│       │   │   └── SecurityConfig.java         # OAuth2 Resource Server + roles por claim
│       │   ├── controller/
│       │   │   └── GuiaController.java         # CRUD de guías + operaciones S3
│       │   ├── dto/
│       │   │   ├── request/                    # GuiaRequestDTO
│       │   │   └── response/                   # GuiaResponseDTO
│       │   ├── entity/
│       │   │   └── GuiaDespacho.java           # Tabla guias_despacho
│       │   ├── enums/
│       │   │   └── EstadoGuia.java             # PENDIENTE | ENVIADO | ENTREGADO
│       │   ├── repository/
│       │   │   └── GuiaDespachoRepository.java
│       │   └── service/
│       │       ├── GuiaService.java
│       │       └── impl/
│       │           └── GuiaServiceImpl.java    # Lógica: EFS + S3 + PDF
│       └── resources/
│           └── application.properties          # Configuración base (H2 local)
├── .env.example                               # Plantilla de variables de entorno
└── Dockerfile                                 # Build multi-stage Maven → JRE Alpine
```

---

## Variables de entorno

| Variable | Descripción | Valor por defecto (local) |
|---|---|---|
| `SPRING_PROFILE` | Perfil activo de Spring | `local` |
| `PORT` | Puerto del servidor Tomcat | `8080` |
| `EFS_TEMP_PATH` | Ruta donde se almacenan los PDFs temporalmente | `target/efs/guias` |
| `AZURE_ISSUER_URI` | Issuer URI del tenant Azure AD B2C | — |
| `AZURE_JWK_SET_URI` | URI de claves públicas para verificar el JWT | — |
| `AWS_S3_BUCKET_NAME` | Nombre del bucket S3 | `mi-bucket-dummy` |
| `AWS_ACCESS_KEY_ID` | Clave de acceso IAM | `dummy-access-key` |
| `AWS_SECRET_ACCESS_KEY` | Secreto IAM | `dummy-secret-key` |
| `AWS_SESSION_TOKEN` | Token de sesión temporal (AWS Academy) | _(vacío)_ |
| `AWS_REGION` | Región de AWS | `us-east-1` |

Para ejecución local, copia `.env.example` a `.env` y completa los valores reales. El archivo `.env` está excluido del repositorio vía `.gitignore`.

En producción todas las variables se inyectan desde **GitHub Secrets** a través del `docker run` en el pipeline CI/CD.

---

## Ejecución local

### Requisitos previos

- Java 17+
- Maven 3.8+
- Archivo `.env` con `AZURE_ISSUER_URI` y `AZURE_JWK_SET_URI` configurados

### Levantar la aplicación

```bash
git clone https://github.com/<usuario>/despacho-service.git
cd despacho-service
mvn spring-boot:run
```

La aplicación levanta en `http://localhost:8080`.

### Consola H2

Disponible en `http://localhost:8080/h2-console` con los siguientes datos de conexión:

- **JDBC URL:** `jdbc:h2:mem:despachodb`
- **User:** `sa`
- **Password:** _(vacío)_

### Ejecutar con Docker

```bash
docker build -t despacho-service .
docker run -p 8080:8080 \
  -e AZURE_ISSUER_URI=<tu-issuer-uri> \
  -e AZURE_JWK_SET_URI=<tu-jwk-set-uri> \
  despacho-service
```

---

## Endpoints disponibles

Todos los endpoints requieren el header:
```
Authorization: Bearer <token-jwt-azure>
```

El token se obtiene ejecutando el User Flow de Azure AD B2C e iniciando sesión con un usuario que tenga el custom claim `extension_rolDespacho` configurado.

---

#### `POST /api/guias` — Crear guía
Registra la guía en base de datos y genera el PDF en EFS. Requiere rol `gestion`.

**Request body:**
```json
{
  "numeroGuia": "G-001",
  "transportista": "Transportes del Sur",
  "fecha": "2026-06-08",
  "direccionOrigen": "Av. Alemania 1234, Valdivia",
  "direccionDestino": "Calle Los Robles 567, Puerto Montt",
  "descripcionCarga": "Cajas de frutas, 200 kg",
  "estado": "PENDIENTE"
}
```

**Response `200`:**
```json
{
  "id": 1,
  "numeroGuia": "G-001",
  "transportista": "Transportes del Sur",
  "fecha": "2026-06-08",
  "direccionOrigen": "Av. Alemania 1234, Valdivia",
  "direccionDestino": "Calle Los Robles 567, Puerto Montt",
  "descripcionCarga": "Cajas de frutas, 200 kg",
  "estado": "PENDIENTE",
  "rutaEfs": "target/efs/guias/guia-1-g-001.pdf",
  "rutaS3": null
}
```

---

#### `POST /api/guias/{id}/subir` — Subir guía a S3
Lee el PDF desde EFS y lo publica en S3. Actualiza el estado a `ENVIADO`. Requiere rol `gestion`.

```
POST /api/guias/1/subir
```

**Response `200`:**
```json
{
  "id": 1,
  "estado": "ENVIADO",
  "rutaEfs": "target/efs/guias/guia-1-g-001.pdf",
  "rutaS3": "2026/06/08/transportes-del-sur/guia-g-001.pdf"
}
```

---

#### `GET /api/guias/{id}/descargar` — Descargar guía
Descarga el PDF. Requiere rol `descarga`. Si el archivo existe en EFS se sirve desde ahí; de lo contrario se descarga desde S3.

```
GET /api/guias/1/descargar
```

**Response `200`:** archivo `guia-1.pdf` (`application/pdf`)

**Response `403`:** si el token no tiene el rol `descarga`.

---

#### `PUT /api/guias/{id}` — Actualizar guía
Regenera el PDF en EFS y elimina la copia anterior de S3 si existía. Requiere rol `gestion`.

**Request body:** misma estructura que `POST /api/guias`.

---

#### `DELETE /api/guias/{id}` — Eliminar guía
Elimina el registro de base de datos, el PDF en EFS y el objeto en S3 (si existe). Requiere rol `gestion`.

**Response `204 No Content`**

---

#### `GET /api/guias/buscar` — Consultar por transportista y fecha
Requiere rol `gestion`.

```
GET /api/guias/buscar?transportista=Transportes del Sur&fecha=2026-06-08
```

**Response `200`:**
```json
[
  {
    "id": 1,
    "numeroGuia": "G-001",
    "transportista": "Transportes del Sur",
    "fecha": "2026-06-08",
    "estado": "PENDIENTE",
    "rutaEfs": "target/efs/guias/guia-1-g-001.pdf",
    "rutaS3": null
  }
]
```

---

## Roles y permisos

La autorización se gestiona mediante el custom claim `extension_rolDespacho` configurado en Azure AD B2C. Cada usuario tiene asignado uno de los siguientes valores:

| Valor del claim | Acceso permitido |
|---|---|
| `descarga` | `GET /api/guias/{id}/descargar` únicamente |
| `gestion` | Todos los demás endpoints (crear, subir, actualizar, eliminar, consultar) |

Cualquier petición con un token que no tenga el claim requerido para el endpoint recibe una respuesta `403 Forbidden`.

---

## Configuración de AWS EFS en EC2

El EFS actúa como el sistema de archivos compartido donde el contenedor escribe los PDFs antes de subirlos a S3.

### Paso 1 — Crear el sistema de archivos EFS en AWS

1. Ir a **AWS Console → EFS → Create file system**.
2. Seleccionar la misma VPC que la instancia EC2.
3. Verificar que el Security Group del EFS permite tráfico **NFS (puerto 2049)** desde el Security Group de la EC2.

### Paso 2 — Montar el EFS en la instancia EC2

Ejecutar los siguientes comandos **una sola vez** al configurar el servidor:

```bash
# Instalar el cliente NFS
sudo yum install -y amazon-efs-utils        # Amazon Linux
# sudo apt-get install -y nfs-common        # Ubuntu

# Crear el directorio de montaje
sudo mkdir -p /home/ec2-user/efs

# Montar el EFS (reemplazar <EFS_DNS> con el DNS del sistema de archivos EFS)
sudo mount -t nfs4 \
  -o nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2 \
  <EFS_DNS>:/ /home/ec2-user/efs

# Crear la carpeta de guías dentro del EFS
sudo mkdir -p /home/ec2-user/efs/guias
sudo chmod 777 /home/ec2-user/efs/guias

# (Opcional) Hacer el montaje persistente tras reinicios de la instancia
echo "<EFS_DNS>:/ /home/ec2-user/efs nfs4 nfsvers=4.1,rsize=1048576,wsize=1048576,hard,timeo=600,retrans=2,_netdev 0 0" \
  | sudo tee -a /etc/fstab
```

### Paso 3 — Verificar el montaje

```bash
df -h | grep efs
# Salida esperada:
# 127.0.0.1:/  8.0E  0  8.0E  0%  /home/ec2-user/efs
```

### Cómo se conecta el EFS al contenedor

El pipeline arranca el contenedor con `-v /home/ec2-user/efs/guias:/tmp/guias`, montando el directorio EFS físico dentro del contenedor:

```bash
docker run -d \
  -v /home/ec2-user/efs/guias:/tmp/guias \
  -e EFS_TEMP_PATH=/tmp/guias \
  despacho-service:latest
```

---

## Pipeline CI/CD

El archivo `.github/workflows/deploy.yaml` implementa tres jobs encadenados que se activan automáticamente con cada `push` a `main`:

```
push → main
    │
    ├── Job 1: build
    │     └── mvn clean package -DskipTests
    │
    ├── Job 2: docker  (needs: build)
    │     ├── docker build
    │     └── docker push → Docker Hub (:latest + :sha)
    │
    └── Job 3: deploy  (needs: docker)
          ├── SSH → EC2
          ├── docker pull
          ├── docker stop / rm (contenedor anterior)
          ├── docker run (variables de entorno + volumen EFS)
          └── docker image prune
```

Si cualquier job falla, los siguientes no se ejecutan.

---

## GitHub Secrets requeridos

Configurar en **Settings → Secrets and variables → Actions** del repositorio:

| Secret | Descripción |
|---|---|
| `DOCKERHUB_USERNAME` | Usuario de Docker Hub |
| `DOCKERHUB_PASSWORD` | Token de acceso de Docker Hub |
| `EC2_HOST` | IP pública de la instancia EC2 |
| `EC2_USER` | Usuario SSH (`ec2-user` en Amazon Linux, `ubuntu` en Ubuntu) |
| `EC2_SSH_KEY` | Contenido completo del archivo `.pem` de la instancia |
| `AZURE_ISSUER_URI` | Issuer URI del tenant de Azure AD B2C |
| `AZURE_JWK_SET_URI` | URI de claves públicas JWKS de Azure AD B2C |
| `AWS_ACCESS_KEY_ID` | Clave de acceso IAM con permisos sobre S3 |
| `AWS_SECRET_ACCESS_KEY` | Secreto IAM asociado |
| `AWS_SESSION_TOKEN` | Token de sesión temporal de AWS Academy |
| `AWS_S3_BUCKET_NAME` | Nombre del bucket S3 destino |
| `AWS_REGION` | Región AWS del bucket (ej. `us-east-1`) |

##
