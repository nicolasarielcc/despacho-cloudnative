# Criterio 7 — Presenta el proyecto en video (10 pts)

## Objetivo del Criterio

Presentar el proyecto en un video claro y completo, demostrando todas las funcionalidades requeridas, con dominio de la temática y desarrollo de la actividad.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Grabación en Teams con compañero/a de grupo | ✅ | Reunión de Teams grabada |
| Mostrar funcionamiento de microservicios | ✅ | Demo en vivo |
| Ejecución y pruebas mediante Postman | ✅ | Capturas de cada endpoint |
| Duración: 5 a 10 minutos | ✅ | Mínimo 5, máximo 10 minutos |
| Explicar lo que se visualiza | ✅ | Narración continua durante el video |
| Link de grabación en Teams | ✅ | Pegar en AVA |
| Repositorio GitHub | ✅ | Link en la entrega |
| ZIP/RAR con documentación Word | ✅ | Paso a paso + evidencias Postman |

---

## Guion del Video (8 minutos recomendado)

### Minuto 0:00-0:30 — Introducción

> "Hola, somos [Nombre1] y [Nombre2]. Vamos a presentar la Sumativa 3 de Desarrollo Cloud Native: Sistema de Gestión de Pedidos y Generación de Guías de Despacho para una empresa transportista."

- [ ] Mostrar a los 2 integrantes en cámara
- [ ] Decir el nombre del proyecto y la asignatura

### Minuto 0:30-2:00 — Código fuente (GitHub)

> "Comencemos mostrando el repositorio en GitHub y la estructura del proyecto."

- [ ] Abrir navegador → Repositorio GitHub
- [ ] Mostrar estructura de carpetas del proyecto
- [ ] Destacar:
  - `pom.xml` (dependencias)
  - `GuiaDespacho.java` y `GuiaDespachoProcesada.java` (las 2 tablas)
  - `GuiaDespachoController.java` (7 endpoints)
  - `SecurityConfig.java` (seguridad con roles)
  - `RabbitMQConfig.java` (config de colas)
  - `deploy.yml` (pipeline CI/CD)
- [ ] Mostrar que el pipeline CI/CD se ejecutó exitosamente (Actions → ✅ verde)

### Minuto 2:00-3:00 — RabbitMQ (colas)

> "Ahora mostremos la mensajería asíncrona con RabbitMQ."

- [ ] Abrir Docker Desktop o terminal: `docker ps` → mostrar contenedor rabbitmq
- [ ] Abrir http://localhost:15672
- [ ] Mostrar:
  - Exchange `exchange-guias` con sus 2 bindings
  - Cola `cola-guias-exitosas`
  - Cola `cola-guias-error`
- [ ] Explicar el flujo: éxito → cola 1, error → cola 2

### Minuto 3:00-4:30 — Azure AD B2C (autenticación)

> "La autenticación y autorización se gestionan con Azure AD B2C."

- [ ] Abrir Azure Portal → Azure AD B2C
- [ ] Mostrar:
  - Tenant `TransportistaB2C`
  - App registration `transportista-backend`
  - Custom claim `extension_consultaRole`
  - User Flow `B2C_1_signup_signin_transportista`
- [ ] Ejecutar User Flow → iniciar sesión con usuario admin
- [ ] Mostrar `jwt.ms` → claim `extension_consultaRole: "admin"`
- [ ] Ejecutar User Flow → iniciar sesión con usuario consulta
- [ ] Mostrar `jwt.ms` → claim `extension_consultaRole: "consulta"`

### Minuto 4:30-5:00 — API Gateway

> "Todos los endpoints están registrados y securitizados en AWS API Gateway."

- [ ] Abrir AWS Console → API Gateway
- [ ] Mostrar las 7 rutas configuradas
- [ ] Mostrar el autorizador JWT con el issuer de Azure
- [ ] Mostrar el stage `produccion` desplegado

### Minuto 5:00-7:30 — Demostración Postman (todos los endpoints)

> "Ahora vamos a probar todos los endpoints en Postman."

#### Prueba 1 — Crear guía (POST /api/guias)
- [ ] Usar token de rol **admin**
- [ ] Enviar POST con body JSON
- [ ] Mostrar respuesta 201 Created con el código GD-* autogenerado

#### Prueba 2 — Consultar guías (GET /api/guias)
- [ ] Filtrar por transportista y fecha
- [ ] Mostrar respuesta 200 OK con lista de guías

#### Prueba 3 — Modificar guía (PUT /api/guias/{id})
- [ ] Cambiar transportista/origen/destino
- [ ] Mostrar respuesta 200 OK con datos actualizados

#### Prueba 4 — Subir a S3 (POST /api/guias/{id}/subir-s3)
- [ ] Mostrar respuesta con urlS3 completado
- [ ] Abrir consola S3 → mostrar el archivo subido
- [ ] Navegar por la estructura de carpetas

#### Prueba 5 — Descargar guía (GET /api/guias/{id}/descargar)
- [ ] Cambiar a token de rol **consulta** (IMPORTANTE)
- [ ] Mostrar respuesta 200 OK
- [ ] Intentar la misma petición con token admin → 403 Forbidden
- [ ] Intentar PUT /api/guias/{id} con token consulta → 403 Forbidden

#### Prueba 6 — Eliminar guía (DELETE /api/guias/{id})
- [ ] Usar token admin
- [ ] Mostrar respuesta 204 No Content

#### Prueba 7 — Procesar cola 1 (POST /api/cola/procesar-guias)
- [ ] Usar token admin
- [ ] Mostrar respuesta 200 OK con guías procesadas
- [ ] Mostrar la tabla GUIA_DESPACHO_PROCESADA en Oracle Cloud

#### Prueba 8 — Sin token → 401
- [ ] Quitar el token de Postman
- [ ] Intentar GET /api/guias → 401 Unauthorized

### Minuto 7:30-8:00 — Cierre

> "Con esto demostramos todas las funcionalidades requeridas del sistema: microservicios Spring Boot, mensajería asíncrona con RabbitMQ, seguridad con Azure AD B2C y Spring Security, almacenamiento S3, API Gateway, y pipeline CI/CD. ¡Gracias!"

---

## Configuración Técnica de la Grabación

### Crear reunión en Teams

1. Abre **Microsoft Teams**
2. Haz clic en **Calendario** → **Nueva reunión**
3. Invita a tu compañero/a
4. Inicia la reunión
5. Haz clic en **... (Más acciones)** → **Iniciar grabación**

### Compartir pantalla

Durante la grabación, comparte tu pantalla completa para que se vea:
- VS Code
- Navegador (GitHub, Azure Portal, AWS Console, RabbitMQ UI)
- Postman
- Terminal (Docker, logs)

### Calidad de audio/video

- [ ] Usar micrófono (no el del notebook)
- [ ] Hablar claro y pausado
- [ ] Evitar ruido de fondo
- [ ] Ambos integrantes deben hablar (turnarse)

---

## Entregables Requeridos por el Formato de Respuesta

### 1. Link al repositorio GitHub
```
https://github.com/{TU_USUARIO}/transportista-app
```

### 2. ZIP/RAR con documentación Word

Estructura del documento Word:

```
DOCUMENTACIÓN SUMATIVA 3
Sistema de Gestión de Guías de Despacho

1. INTRODUCCIÓN
   - Descripción del sistema
   - Arquitectura (diagrama)

2. CONFIGURACIÓN API GATEWAY (paso a paso)
   - Creación de API HTTP
   - Rutas configuradas
   - Integraciones HTTP URI
   - Autorizador JWT
   - Stage desplegado

3. CONFIGURACIÓN AZURE AD B2C (paso a paso)
   - Creación del tenant B2C
   - Registro de aplicación
   - Custom claims
   - User flows
   - Roles y permisos

4. EVIDENCIAS DE EJECUCIÓN EN POSTMAN
   - Captura de cada endpoint con su respuesta
   - Captura de errores (401, 403) según corresponda
   - Captura de jwt.ms mostrando claims

5. CONFIGURACIÓN RABBITMQ
   - Colas, exchanges, bindings
   - Evidencia de mensajes fluyendo

6. CONFIGURACIÓN S3
   - Bucket creado
   - Estructura de carpetas
   - Archivos subidos

7. CI/CD (GitHub Actions)
   - Secrets configurados
   - Pipeline ejecutado exitosamente
```

### 3. Link de la grabación en Teams

Después de la reunión, Teams generará un link. Pégalo en el AVA junto con el ZIP.

---

## Checklist de Verificación

- [ ] Video grabado en Teams (5-10 minutos)
- [ ] Ambos integrantes aparecen y hablan
- [ ] Guion cubierto: código, RabbitMQ, Azure, API Gateway, Postman, S3, CI/CD
- [ ] Demostración de todos los endpoints (7) en Postman
- [ ] Demostración de errores: 401 (sin token), 403 (rol incorrecto)
- [ ] Rol consulta solo descarga (endpoint exclusivo)
- [ ] Rol admin hace todo menos descargar
- [ ] Colas RabbitMQ mostradas en la UI
- [ ] Estructura S3 mostrada en consola AWS
- [ ] Pipeline CI/CD mostrado en GitHub Actions
- [ ] Documento Word con paso a paso y capturas
- [ ] ZIP/RAR subido al AVA
- [ ] Link de GitHub incluido en la entrega
- [ ] Link de Teams pegado en el AVA
