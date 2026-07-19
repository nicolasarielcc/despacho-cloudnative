package com.transportista.controller;

import com.transportista.dto.CursoRequest;
import com.transportista.dto.CursoResponse;
import com.transportista.dto.InscripcionResponse;
import com.transportista.service.CursoService;
import com.transportista.service.InscripcionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de cursos.
 *
 * Expone 8 endpoints REST:
 * 1. POST   /api/cursos                       - Crear curso
 * 2. PUT    /api/cursos/{id}                   - Modificar curso
 * 3. DELETE /api/cursos/{id}                   - Eliminar curso
 * 4. GET    /api/cursos                        - Consultar cursos por instructor y fecha
 * 5. POST   /api/cursos/{id}/subir-s3          - Subir curso a S3
 * 6. GET    /api/cursos/{id}/descargar         - Descargar curso
 * 7. GET    /api/cola/consumir-mensaje         - Consumir mensaje de cola via HTTP
 * 8. POST   /api/cola/procesar-inscripciones   - Procesar inscripciones de la cola
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CursoController {

    private final CursoService cursoService;
    private final InscripcionService inscripcionService;

    // ================================================================
    // ENDPOINT 1: CREAR CURSO
    // POST /api/cursos
    // Rol requerido: instructor
    // ================================================================

    @PostMapping("/cursos")
    public ResponseEntity<CursoResponse> crearCurso(
            @Valid @RequestBody CursoRequest request) {
        log.info("POST /api/cursos — Creando curso: {}", request.getNombre());
        CursoResponse response = cursoService.crearCurso(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // ENDPOINT 2: MODIFICAR CURSO
    // PUT /api/cursos/{id}
    // Rol requerido: instructor
    // ================================================================

    @PutMapping("/cursos/{id}")
    public ResponseEntity<CursoResponse> modificarCurso(
            @PathVariable Long id,
            @Valid @RequestBody CursoRequest request) {
        log.info("PUT /api/cursos/{} — Modificando curso", id);
        CursoResponse response = cursoService.modificarCurso(id, request);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 3: ELIMINAR CURSO
    // DELETE /api/cursos/{id}
    // Rol requerido: instructor
    // ================================================================

    @DeleteMapping("/cursos/{id}")
    public ResponseEntity<Void> eliminarCurso(@PathVariable Long id) {
        log.info("DELETE /api/cursos/{} — Eliminando curso", id);
        cursoService.eliminarCurso(id);
        return ResponseEntity.noContent().build();
    }

    // ================================================================
    // ENDPOINT 4: CONSULTAR CURSOS POR INSTRUCTOR Y FECHA
    // GET /api/cursos?instructor={i}&fecha={yyyy-MM-dd}
    // Rol requerido: instructor
    // ================================================================

    @GetMapping("/cursos")
    public ResponseEntity<List<CursoResponse>> consultarCursos(
            @RequestParam(required = false) String instructor,
            @RequestParam(required = false) String fecha) {
        log.info("GET /api/cursos — Consultando. Instructor: {}, Fecha: {}", instructor, fecha);
        List<CursoResponse> cursos = cursoService.consultarCursos(instructor, fecha);
        return ResponseEntity.ok(cursos);
    }

    // ================================================================
    // ENDPOINT 5: SUBIR CURSO A S3
    // POST /api/cursos/{id}/subir-s3
    // Rol requerido: instructor
    // ================================================================

    @PostMapping("/cursos/{id}/subir-s3")
    public ResponseEntity<CursoResponse> subirCursoAS3(@PathVariable Long id) {
        log.info("POST /api/cursos/{}/subir-s3 — Subiendo curso a S3", id);
        CursoResponse response = cursoService.subirCursoAS3(id);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 6: DESCARGAR CURSO
    // GET /api/cursos/{id}/descargar
    // Rol requerido: estudiante
    // ================================================================

    @GetMapping("/cursos/{id}/descargar")
    public ResponseEntity<CursoResponse> descargarCurso(@PathVariable Long id) {
        log.info("GET /api/cursos/{}/descargar — Descargando curso", id);
        CursoResponse response = cursoService.descargarCurso(id);
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 7: CONSUMIR MENSAJE DE COLA PRINCIPAL (HTTP PULL)
    // GET /api/cola/consumir-mensaje
    // Rol requerido: instructor
    // ================================================================

    @GetMapping("/cola/consumir-mensaje")
    public ResponseEntity<CursoResponse> consumirMensajeDeCola() {
        log.info("GET /api/cola/consumir-mensaje — Consumiendo mensaje de cola principal via HTTP");
        CursoResponse response = cursoService.consumirMensajeDeCola();
        if (response == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(response);
    }

    // ================================================================
    // ENDPOINT 8: PROCESAR INSCRIPCIONES DE LA COLA
    // POST /api/cola/procesar-inscripciones
    // Rol requerido: instructor
    // ================================================================

    @PostMapping("/cola/procesar-inscripciones")
    public ResponseEntity<List<InscripcionResponse>> procesarCola() {
        log.info("POST /api/cola/procesar-inscripciones — Procesando inscripciones");
        List<InscripcionResponse> procesadas = inscripcionService.procesarColaYGuardar();
        return ResponseEntity.ok(procesadas);
    }
}
