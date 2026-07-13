# Checklist — Verificación Completa del Formato de Respuesta (Forma C)

Este documento verifica que **cada requisito** del formato de respuesta (Instrucciones Específicas — Sumativa 3, Forma C) esté cubierto por al menos un criterio de la pauta de evaluación.

---

## Caso: Empresa Transportista — Sistema de Gestión de Pedidos y Generación de Guías de Despacho

### Requisitos de Infraestructura Cloud

| # | Requisito | Criterio(s) | Estado | Evidencia |
|---|-----------|:-----------:|:------:|-----------|
| R1 | Colas RabbitMQ desplegadas en contenedor Docker | Criterio 2 | ✅ Documentado | `docker-compose.yml` + `docs/criterio-2-rabbitmq.md` Paso 1 |
| R2 | Productores y consumidores desarrollados en Java | Criterio 2 | ✅ Implementado | `GuiaProducer.java` + `GuiaConsumer.java` |
| R3 | 2 colas: cola 1 (guias exitosas), cola 2 (mensajes con error) | Criterio 2 | ✅ Implementado | `RabbitMQConfig.java`: `cola-guias-exitosas` + `cola-guias-error` |
| R4 | 1 componente que transmita mensajes en ambas colas | Criterio 2 | ✅ Implementado | `GuiaProducer.java`: `enviarGuiaExitosa()` + `enviarGuiaError()` |
| R5 | API Gateway con todos los endpoints registrados | Criterio 4 | ✅ Documentado | `docs/criterio-4-api-gateway.md` Paso 4 |
| R6 | API Gateway con endpoints securitizados | Criterio 4 | ✅ Documentado | `docs/criterio-4-api-gateway.md` Paso 6 |
| R7 | Autenticación del backend integrada con Azure AD | Criterio 5 | ✅ Documentado | `docs/criterio-5-azure-ad-b2c.md` Paso 3-10 |
| R8 | Autenticación del API Gateway integrada con Azure AD | Criterio 4 + 5 | ✅ Documentado | Autorizador JWT en API Gateway usa issuer de Azure |
| R9 | 2 roles: uno para descargar guías, otro para el resto de endpoints | Criterio 3 + 5 | ✅ Implementado | `extension_consultaRole` = `consulta` (descargar) / `admin` (resto) |
| R10 | Backend securitizado mediante Spring Security en todos los endpoints | Criterio 3 | ✅ Implementado | `SecurityConfig.java` protege todas las rutas `/api/**` |

### Requisitos Funcionales

| # | Requisito | Criterio(s) | Estado | Evidencia |
|---|-----------|:-----------:|:------:|-----------|
| R11 | Sistema de guías: enviar datos a cola 1 | Criterio 1 + 2 | ✅ Implementado | `GuiaDespachoService.crearGuia()` → `guiaProducer.enviarGuiaExitosa()` |
| R12 | Si falla la guía, enviar a cola 2 (mensajes con error) | Criterio 1 + 2 | ✅ Implementado | `GuiaDespachoService.crearGuia()` → catch → `guiaProducer.enviarGuiaError()` |
| R13 | Endpoint adicional para consumir cola 1 | Criterio 1 | ✅ Implementado | `POST /api/cola/procesar-guias` |
| R14 | Cola 1 → guardar en BD Oracle en tabla DISTINTA | Criterio 1 | ✅ Implementado | `GuiaDespachoProcesada` (tabla NUEVA con `fechaProcesamiento`) |
| R15 | CRUD de guías de despacho | Criterio 1 | ✅ Implementado | POST, PUT, DELETE, GET en `GuiaDespachoController` |
| R16 | Subir guías generadas a S3 | Criterio 1 + 6 | ✅ Implementado | `POST /api/guias/{id}/subir-s3` + `S3Service` |
| R17 | Descargar guías con validación de permisos | Criterio 1 + 3 + 6 | ✅ Implementado | `GET /api/guias/{id}/descargar` (solo rol consulta) |
| R18 | Modificar o actualizar guías | Criterio 1 | ✅ Implementado | `PUT /api/guias/{id}` |
| R19 | Eliminar guías específicas | Criterio 1 | ✅ Implementado | `DELETE /api/guias/{id}` |
| R20 | Consultar guías por transportista y fecha | Criterio 1 | ✅ Implementado | `GET /api/guias?transportista=X&fecha=YYYY-MM-DD` |

### Requisitos de Desarrollo

| # | Requisito | Criterio(s) | Estado | Evidencia |
|---|-----------|:-----------:|:------:|-----------|
| R21 | Usar Docker para desarrollo | Criterio 2 | ✅ Implementado | `docker-compose.yml` con RabbitMQ |
| R22 | Cambiar URLs a las de EC2 en producción | Criterio 4 | ✅ Documentado | Integraciones HTTP URI → `http://{IP-EC2}:8080/...` |
| R23 | CI/CD con GitHub Actions | Criterio 4 | ✅ Implementado | `.github/workflows/deploy.yml` |
| R24 | Pipeline construye imagen Docker | CI/CD | ✅ Implementado | `deploy.yml` Paso 3-4 |
| R25 | Pipeline despliega en EC2 automáticamente | CI/CD | ✅ Implementado | `deploy.yml` Paso 6 |

### Requisitos de Entrega

| # | Requisito | Criterio(s) | Estado | Evidencia |
|---|-----------|:-----------:|:------:|-----------|
| R26 | Link al repositorio GitHub | Criterio 7 | 📋 Pendiente | Subir proyecto a GitHub |
| R27 | ZIP/RAR con documentación Word | Criterio 7 | 📋 Pendiente | `docs/criterio-7-video.md` — Estructura del documento |
| R28 | Documentación: paso a paso API Gateway | Criterio 7 | 📋 Pendiente | `docs/criterio-4-api-gateway.md` → Word |
| R29 | Documentación: paso a paso Azure AD B2C | Criterio 7 | 📋 Pendiente | `docs/criterio-5-azure-ad-b2c.md` → Word |
| R30 | Evidencias de ejecución en Postman | Criterio 7 | 📋 Pendiente | Capturas de cada endpoint |
| R31 | Video en Teams (5-10 minutos) | Criterio 7 | 📋 Pendiente | `docs/criterio-7-video.md` — Guion del video |
| R32 | Ambos integrantes participan | Criterio 7 | 📋 Pendiente | Turnarse en la presentación |

---

## Resumen de Cobertura por Criterio de Evaluación

| Criterio | Puntaje | Requisitos cubiertos | Documentación |
|----------|:-------:|----------------------|---------------|
| **1. Microservicios Spring Boot** | 20 pts | R11-R20 | `docs/criterio-1-microservicios.md` |
| **2. Colas RabbitMQ** | 15 pts | R1-R4 | `docs/criterio-2-rabbitmq.md` |
| **3. Spring Security** | 15 pts | R9, R10, R17 | `docs/criterio-3-security.md` |
| **4. API Gateway** | 15 pts | R5-R6 | `docs/criterio-4-api-gateway.md` |
| **5. Azure AD B2C** | 15 pts | R7-R9 | `docs/criterio-5-azure-ad-b2c.md` |
| **6. Subida a S3** | 10 pts | R16 | `docs/criterio-6-s3.md` |
| **7. Video presentación** | 10 pts | R26-R32 | `docs/criterio-7-video.md` |

---

## Lo que ya está implementado en código

✅ **Completado:**
- Proyecto Spring Boot con 15 clases Java
- 2 entidades JPA (GUIA_DESPACHO + GUIA_DESPACHO_PROCESADA como tabla NUEVA)
- 7 endpoints REST (6 requeridos + 1 adicional de cola)
- RabbitMQ: productor, consumidor, config (2 colas, exchange direct, bindings, DLX)
- Spring Security: roles admin/consulta, validación JWT, toggle desarrollo/producción
- Docker: Dockerfile multi-stage + docker-compose.yml
- CI/CD: pipeline GitHub Actions deploy.yml
- 54 tests unitarios y de integración (0 fallos)
- Perfiles: dev (H2 + seguridad off) y test (H2 + tests)
- README.md con documentación completa
- 7 guías paso a paso en `docs/`

📋 **Pendiente (requiere servicios Cloud):**
- Configurar Oracle Cloud (credenciales en application.properties)
- Crear colas y exchange en UI de RabbitMQ
- Configurar Azure AD B2C (tenant, app, user flows, claims, roles)
- Configurar AWS API Gateway (rutas, autorizador JWT, deploy)
- Configurar AWS S3 (bucket, credenciales)
- Configurar GitHub Secrets para CI/CD
- Crear EC2 y desplegar la aplicación
- Grabar video en Teams
- Crear documento Word con paso a paso

---

## Instrucciones para completar lo pendiente

1. **Oracle Cloud:** Seguir `docs/criterio-1-microservicios.md` → Paso 7
2. **RabbitMQ (UI):** Seguir `docs/criterio-2-rabbitmq.md` → Paso 2
3. **Azure AD B2C:** Seguir `docs/criterio-5-azure-ad-b2c.md` → Pasos 1-12
4. **API Gateway:** Seguir `docs/criterio-4-api-gateway.md` → Pasos 1-9
5. **S3:** Seguir `docs/criterio-6-s3.md` → Pasos 1-5
6. **GitHub + CI/CD:** Seguir `docs/criterio-4-api-gateway.md` → Paso 9
7. **Video:** Seguir `docs/criterio-7-video.md` → Guion y checklist
