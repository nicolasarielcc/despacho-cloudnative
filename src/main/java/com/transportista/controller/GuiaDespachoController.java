package com.transportista.controller;

import com.transportista.dto.GuiaDespachoRequest;
import com.transportista.dto.GuiaDespachoResponse;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.service.GuiaDespachoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de guías de despacho.
 *
 * Expone 6 endpoints REST según los requerimientos de la Sumativa 3:
 * 1. POST   /api/guias                       - Crear guía de despacho
 * 2. PUT    /api/guias/{id}                   - Modificar / actualizar guía
 * 3. DELETE /api/guias/{id}                   - Eliminar guía específica
 * 4. GET    /api/guias                        - Consultar guías por transportista y fecha
 * 5. POST   /api/guias/{id}/subir-s3          - Subir guía generada a S3
 * 6. GET    /api/guias/{id}/descargar         - Descargar guía con validación de permisos
 *
 * Endpoint adicional (requerimiento Sumativa 3):
 * 7. POST   /api/cola/procesar-guias          - Consumir cola 1 y guardar en tabla nueva
 *
 * IMPORTANTE:
 * - Todos los endpoints están protegidos con Spring Security + JWT (Azure AD B2C)
 * - Los roles se configuran en SecurityConfig.java a nivel HTTP:
 *   - Rol "admin":    acceso a crear, modificar, eliminar, consultar, subir y procesar
 *   - Rol "consulta": acceso SOLO a descargar guías
 * - Estos roles corresponden al custom claim "extension_consultaRole"
 *   configurado en Azure AD B2C (User Flows)
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class GuiaDespachoController {

    private final GuiaDespachoService guiaService;

    // ================================================================
    // ENDPOINT 1: CREAR GUÍA DE DESPACHO
    // POST /api/guias
    // Rol requerido: admin
    // ================================================================

    /**
     * Crea una nueva guía de despacho.
     *
     * La guía se crea con estado PENDIENTE, se guarda en Oracle Cloud,
     * y se envía a la cola RabbitMQ (cola-guias-exitosas).
     * Si el envío a la cola falla, la guía va a la cola de errores (cola-guias-error).
     *
     * Ejemplo de request body:
     * {
     *   "transportista": "Juan Perez",
     *   "fechaEmision": "2025-01-15T10:30:00",
     *   "origen": "Santiago",
     *   "destino": "Valparaíso",
     *   "descripcionCarga": "Electrodomésticos - 3 cajas",
     *   "pesoKg": 150.5
     * }
     */
    @PostMapping("/guias")
    public ResponseEntity<GuiaDespachoResponse> crearGuia(
            @Valid @RequestBody GuiaDespachoRequest request) {
        log.info("POST /api/guias — Creando guía de despacho");
        GuiaDespachoResponse response = guiaService.crearGuia(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // ENDPOINT 2: MODIFICAR / ACTUALIZAR GUÍA
    // PUT /api/guias/{id}
    // Rol requerido: admin
    // ================================================================

    /**
     * Modifica una guía de despacho existente.
     */
    @PutMapping("/guias/{id}")
    public ResponseEntity<GuiaDespachoResponse> modificarGuia(
            @PathVariable Long id,
            @Valid @RequestBody GuiaDespachoRequest request) {
        log.info("PUT /api/guias/{} — Modificando guía", id);
        GuiaDespachoResponse response = guiaService.modificarGuia(id, request);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 3: ELIMINAR GUÍA ESPECÍFICA
    // DELETE /api/guias/{id}
    // Rol requerido: admin
    // ================================================================

    /**
     * Elimina una guía de despacho específica por su ID.
     */
    @DeleteMapping("/guias/{id}")
    public ResponseEntity<Void> eliminarGuia(@PathVariable Long id) {
        log.info("DELETE /api/guias/{} — Eliminando guía", id);
        guiaService.eliminarGuia(id);
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    // ENDPOINT 4: CONSULTAR GUÍAS POR TRANSPORTISTA Y FECHA
    // GET /api/guias?transportista={t}&fecha={yyyy-MM-dd}
    // Rol requerido: admin
    // ================================================================

    /**
     * Consulta guías de despacho por transportista y/o fecha.
     *
     * Parámetros opcionales:
     * - transportista: nombre del transportista
     * - fecha: fecha en formato yyyy-MM-dd
     *
     * Si no se envían parámetros, devuelve todas las guías.
     *
     * Ejemplos:
     *   GET /api/guias?transportista=Juan%20Perez&fecha=2025-01-15
     *   GET /api/guias?transportista=Juan%20Perez
     *   GET /api/guias?fecha=2025-01-15
     */
    @GetMapping("/guias")
    public ResponseEntity<List<GuiaDespachoResponse>> consultarGuias(
            @RequestParam(required = false) String transportista,
            @RequestParam(required = false) String fecha) {
        log.info("GET /api/guias — Consultando. Transportista: {}, Fecha: {}", transportista, fecha);
        List<GuiaDespachoResponse> guias = guiaService.consultarGuias(transportista, fecha);
        return ResponseEntity.ok(guias);
    }

    // ================================================================
    // ENDPOINT 5: SUBIR GUÍA A S3
    // POST /api/guias/{id}/subir-s3
    // Rol requerido: admin
    // ================================================================

    /**
     * Sube una guía de despacho al bucket S3.
     *
     * La guía se guarda en la siguiente estructura de carpetas:
     *   guias/{transportista}/{año}/{mes}/guia-{codigo}.pdf
     *
     * La URL de S3 se guarda en el campo urlS3 de la entidad.
     */
    @PostMapping("/guias/{id}/subir-s3")
    public ResponseEntity<GuiaDespachoResponse> subirGuiaAS3(@PathVariable Long id) {
        log.info("POST /api/guias/{}/subir-s3 — Subiendo guía a S3", id);
        GuiaDespachoResponse response = guiaService.subirGuiaAS3(id);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 6: DESCARGAR GUÍA CON VALIDACIÓN DE PERMISOS
    // GET /api/guias/{id}/descargar
    // Rol requerido: consulta (SOLO este rol puede descargar)
    // ================================================================

    /**
     * Descarga una guía de despacho.
     *
     * Este es el ÚNICO endpoint accesible para el rol "consulta".
     * El rol "admin" NO tiene acceso a este endpoint (según requerimiento:
     * "un rol que permita solo usar el endpoint de descargar guías").
     *
     * La seguridad por ruta se configura en SecurityConfig.java:
     *   .requestMatchers(HttpMethod.GET, &quot;&#47;api&#47;guias&#47;&#42;&#47;descargar&quot;).hasAuthority(&quot;consulta&quot;)
     */
    @GetMapping("/guias/{id}/descargar")
    public ResponseEntity<GuiaDespachoResponse> descargarGuia(@PathVariable Long id) {
        log.info("GET /api/guias/{}/descargar — Descargando guía", id);
        GuiaDespachoResponse response = guiaService.descargarGuia(id);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT ADICIONAL: PROCESAR COLA 1 Y GUARDAR EN TABLA NUEVA
    // POST /api/cola/procesar-guias
    // Rol requerido: admin
    // ================================================================

    /**
     * Endpoint adicional requerido por la Sumativa 3.
     *
     * Consume todos los mensajes de la cola-guias-exitosas y los guarda
     * en la tabla GUIA_DESPACHO_PROCESADA (tabla NUEVA, distinta).
     *
     * Esta tabla tiene un campo adicional: fechaProcesamiento.
     */
    @PostMapping("/cola/procesar-guias")
    public ResponseEntity<List<GuiaDespachoProcesada>> procesarColaYGuardar() {
        log.info("POST /api/cola/procesar-guias — Procesando cola 1 y guardando en tabla nueva");
        List<GuiaDespachoProcesada> procesadas = guiaService.procesarColaYGuardar();
        return ResponseEntity.ok(procesadas);
    }

    // ================================================================
    // ENDPOINT ADICIONAL: CONSUMIR MENSAJE DE COLA PRINCIPAL (HTTP PULL)
    // GET /api/cola/consumir-mensaje
    // Rol requerido: admin
    // ================================================================

    @GetMapping("/cola/consumir-mensaje")
    public ResponseEntity<GuiaDespachoResponse> consumirMensajeDeCola() {
        log.info("GET /api/cola/consumir-mensaje — Consumiendo mensaje de cola principal via HTTP");
        GuiaDespachoResponse response = guiaService.consumirMensajeDeCola();
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }
}
