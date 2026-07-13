# Criterio 1 — Microservicios con Spring Boot (20 pts)

## Objetivo del Criterio

Desarrollar todos los microservicios requeridos con funcionalidad completa, siguiendo las especificaciones del caso: una **empresa transportista** necesita un sistema de gestión de pedidos y generación de guías de despacho.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Backend securitizado en su código | ✅ | SecurityConfig.java + @PreAuthorize en Controller |
| Gestionado mediante API Manager | ✅ | Configurado en Criterio 4 |
| Gestionado mediante IDaaS | ✅ | Azure AD B2C en Criterio 5 |
| Desarrollar el backend a partir del caso | ✅ | Entidades GuiaDespacho + GuiaDespachoProcesada |
| Endpoint adicional para consumir cola 1 | ✅ | POST /api/cola/procesar-guias |
| Base de datos Oracle Cloud - tabla nueva | ✅ | GUIA_DESPACHO_PROCESADA (distinta a sumativas anteriores) |
| 6 Endpoints REST especificados | ✅ | Crear, modificar, eliminar, consultar, subir S3, descargar |

---

## Paso 1: Crear el proyecto Spring Boot

### 1.1 Estructura de paquetes

```
src/main/java/com/transportista/
├── TransportistaApplication.java   ← Clase principal con @SpringBootApplication
├── config/                          ← Configuraciones (Security, S3, RabbitMQ)
├── controller/                      ← Endpoints REST
│   └── GuiaDespachoController.java  ← 7 endpoints (6 requeridos + 1 adicional)
├── dto/                             ← Data Transfer Objects
│   ├── GuiaDespachoRequest.java     ← Entrada con validaciones @Valid
│   └── GuiaDespachoResponse.java   ← Salida al frontend
├── entity/                          ← Entidades JPA
│   ├── GuiaDespacho.java            ← Tabla principal (GUIA_DESPACHO)
│   └── GuiaDespachoProcesada.java   ← Tabla NUEVA (GUIA_DESPACHO_PROCESADA)
├── enums/
│   └── EstadoGuia.java              ← PENDIENTE, ENVIADA, CON_ERROR
├── rabbitmq/                        ← Mensajería asíncrona (Criterio 2)
├── repository/                      ← Interfaces JPA
│   ├── GuiaDespachoRepository.java
│   └── GuiaDespachoProcesadaRepository.java
└── service/
    └── GuiaDespachoService.java     ← Lógica de negocio completa
```

### 1.2 Dependencias en pom.xml

Las dependencias clave incluidas:
- `spring-boot-starter-web` → API REST
- `spring-boot-starter-data-jpa` → ORM para Oracle Cloud
- `spring-boot-starter-amqp` → RabbitMQ
- `spring-boot-starter-security` → Seguridad
- `spring-boot-starter-oauth2-resource-server` → JWT de Azure AD B2C
- `spring-cloud-aws-starter-s3` → Almacenamiento S3
- `spring-boot-starter-validation` → Validaciones @Valid
- `spring-boot-starter-actuator` → Monitoreo
- `lombok` → Reduce boilerplate
- `h2` → Base de datos en memoria para desarrollo local

---

## Paso 2: Crear entidades JPA

### 2.1 GuiaDespacho (tabla principal)

**Archivo:** `src/main/java/com/transportista/entity/GuiaDespacho.java`

| Campo | Tipo | Descripción |
|-------|------|-------------|
| `id` | Long (PK) | Autogenerado con secuencia `SEQ_GUIA_DESPACHO` |
| `codigoGuia` | String (único) | Formato: `GD-{timestamp}-{nro}` |
| `transportista` | String | Nombre del transportista |
| `fechaEmision` | LocalDateTime | Fecha/hora de emisión |
| `origen` | String | Dirección de origen |
| `destino` | String | Dirección de destino |
| `descripcionCarga` | String | Descripción de la carga |
| `pesoKg` | Double | Peso en kilogramos |
| `estado` | Enum (EstadoGuia) | PENDIENTE → ENVIADA / CON_ERROR |
| `urlS3` | String | URL en S3 (se completa al subir) |
| `fechaCreacion` | LocalDateTime | Fecha de creación automática |

**Anotaciones:** `@Entity`, `@Table(name = "GUIA_DESPACHO")`, `@Data`, `@Builder`, `@NoArgsConstructor`, `@AllArgsConstructor`

### 2.2 GuiaDespachoProcesada (tabla NUEVA) ⭐

**Archivo:** `src/main/java/com/transportista/entity/GuiaDespachoProcesada.java`

**Requisito clave:** "Se tendrá un endpoint adicional que consumirá los mensajes que se encuentren en la cola 1, y éste los guardará en la base de datos Oracle Cloud **en una tabla distinta a la usada en las sumativas anteriores**."

| Campo adicional | Tipo | Descripción |
|-----------------|------|-------------|
| `fechaProcesamiento` | LocalDateTime | ⭐ Campo NUEVO que diferencia esta tabla de la anterior |

**Anotaciones:** Mismas que GuiaDespacho pero con `@Table(name = "GUIA_DESPACHO_PROCESADA")` y secuencia `SEQ_GUIA_PROCESADA`

### 2.3 Enum EstadoGuia

**Archivo:** `src/main/java/com/transportista/enums/EstadoGuia.java`

```java
public enum EstadoGuia {
    PENDIENTE,   // Recién creada, aún no enviada a cola
    ENVIADA,     // Procesada exitosamente, enviada a cola-exitosas
    CON_ERROR    // Falló procesamiento, enviada a cola-error
}
```

---

## Paso 3: Crear DTOs con validaciones

### 3.1 GuiaDespachoRequest (entrada)

Validaciones Jakarta:
```java
@NotBlank(message = "El transportista es obligatorio")  private String transportista;
@NotNull(message = "La fecha de emisión es obligatoria") private LocalDateTime fechaEmision;
@NotBlank(message = "El origen es obligatorio")          private String origen;
@NotBlank(message = "El destino es obligatorio")         private String destino;
@DecimalMin(value = "0.0")                               private Double pesoKg;
```

### 3.2 GuiaDespachoResponse (salida)

Incluye todos los campos de la entidad más `id`, `codigoGuia`, `estado`, `urlS3`, `fechaCreacion`.

---

## Paso 4: Crear repositorios JPA

### 4.1 GuiaDespachoRepository

```java
@Repository
public interface GuiaDespachoRepository extends JpaRepository<GuiaDespacho, Long> {
    // Consulta requerida: "Consultar guías por transportista y fecha"
    @Query("SELECT g FROM GuiaDespacho g WHERE g.transportista = :transportista AND g.fechaEmision BETWEEN :inicio AND :fin")
    List<GuiaDespacho> findByTransportistaAndFechaEmisionBetween(...);

    Optional<GuiaDespacho> findByCodigoGuia(String codigoGuia);
    List<GuiaDespacho> findByEstado(EstadoGuia estado);
    boolean existsByCodigoGuia(String codigoGuia);
}
```

### 4.2 GuiaDespachoProcesadaRepository

Repositorio para la tabla NUEVA:
```java
@Repository
public interface GuiaDespachoProcesadaRepository extends JpaRepository<GuiaDespachoProcesada, Long> {
    List<GuiaDespachoProcesada> findByCodigoGuia(String codigoGuia);
}
```

---

## Paso 5: Implementar Service (lógica de negocio)

**Archivo:** `src/main/java/com/transportista/service/GuiaDespachoService.java`

### Métodos implementados:

| Método | Lógica |
|--------|--------|
| `crearGuia(request)` | Construye entidad, genera código GD-*, guarda en BD, envía a cola exitosa. Si falla: envía a cola error |
| `modificarGuia(id, request)` | Actualiza campos, reenvía a cola |
| `eliminarGuia(id)` | Elimina de BD |
| `consultarGuias(transportista, fecha)` | Filtra por transportista y/o fecha. Si sin parámetros: todas |
| `subirGuiaAS3(id)` | Genera URL S3 y guarda en entidad |
| `descargarGuia(id)` | Retorna datos de guía para descarga |
| `procesarColaYGuardar()` | ⭐ Consume guías ENVIADA y las guarda en `GUIA_DESPACHO_PROCESADA` |

### Flujo de enrutamiento a colas:

```
crearGuia() / modificarGuia()
    │
    ├── ✔ Éxito → guiaProducer.enviarGuiaExitosa()
    │              └── cola-guias-exitosas
    │
    └── ✖ Error → guiaProducer.enviarGuiaError()
                   └── cola-guias-error
```

---

## Paso 6: Implementar Controller (endpoints REST)

**Archivo:** `src/main/java/com/transportista/controller/GuiaDespachoController.java`

### 7 Endpoints implementados:

| # | Método | Endpoint | Rol | Descripción |
|---|--------|----------|-----|-------------|
| 1 | `POST` | `/api/guias` | admin | Crear guía de despacho |
| 2 | `PUT` | `/api/guias/{id}` | admin | Modificar/actualizar guía |
| 3 | `DELETE` | `/api/guias/{id}` | admin | Eliminar guía específica |
| 4 | `GET` | `/api/guias?transportista=X&fecha=YYYY-MM-DD` | admin | Consultar guías por transportista y fecha |
| 5 | `POST` | `/api/guias/{id}/subir-s3` | admin | Subir guía generada a S3 |
| 6 | `GET` | `/api/guias/{id}/descargar` | consulta | Descargar guía con validación de permisos |
| 7 | `POST` | `/api/cola/procesar-guias` | admin | ⭐ Endpoint adicional: consumir cola 1 y guardar en tabla nueva |

### Ejemplo de request (POST /api/guias):

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

### Ejemplo de respuesta:

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

## Verificación del Criterio

### Tests unitarios y de integración que validan este criterio:

| Clase de test | # Tests | ¿Qué valida? |
|---------------|:-------:|--------------|
| `GuiaDespachoServiceTest$CrearGuiaTests` | 3 | Creación con código automático, envío a cola, manejo de error |
| `GuiaDespachoServiceTest$ModificarGuiaTests` | 2 | Modificación y reenvío a cola |
| `GuiaDespachoServiceTest$EliminarGuiaTests` | 2 | Eliminación, validación de existencia |
| `GuiaDespachoServiceTest$ConsultarGuiasTests` | 2 | Filtro por transportista y fecha |
| `GuiaDespachoServiceTest$ProcesarColaTests` | 2 | Procesamiento cola 1 → tabla NUEVA |
| `GuiaDespachoControllerTest$CrudEndpointsTests` | 8 | Endpoints REST funcionales (POST, PUT, DELETE, GET) |
| `GuiaDespachoControllerTest$DescargaEndpointTests` | 2 | Endpoint de descarga (rol consulta) |

### Ejecutar tests:

```bash
cd transportista/
./mvnw test -DforkCount=0 -Dtest="GuiaDespachoServiceTest,GuiaDespachoControllerTest"
```

### Evidencia para la documentación Word:

1. Captura del proyecto en VS Code mostrando todos los paquetes
2. Captura de Postman con cada endpoint funcionando
3. Captura de la BD Oracle mostrando las 2 tablas (GUIA_DESPACHO y GUIA_DESPACHO_PROCESADA)
4. Captura de los tests pasando en terminal

---

## Checklist de Verificación

- [x] Proyecto Spring Boot con estructura de paquetes organizada
- [x] 2 entidades JPA (GUIA_DESPACHO + GUIA_DESPACHO_PROCESADA como tabla NUEVA)
- [x] Enum EstadoGuia con 3 estados
- [x] DTOs con validaciones Jakarta
- [x] Repositorios JPA con query personalizada (transportista + fecha)
- [x] Service con lógica de negocio completa (CRUD + colas + S3 + procesamiento)
- [x] Controller con 7 endpoints (6 requeridos + 1 adicional de cola)
- [x] Endpoint adicional que consume cola 1 y guarda en tabla NUEVA
- [x] Código de guía autogenerado con formato GD-*
- [x] Tests unitarios y de integración (28 tests para este criterio)
- [ ] Conexión a Oracle Cloud configurada (requiere credenciales reales)
- [ ] Ejecución real contra BD Oracle (paso de producción)
