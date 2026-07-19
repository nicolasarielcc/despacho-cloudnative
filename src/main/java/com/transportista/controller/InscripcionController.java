package com.transportista.controller;

import com.transportista.dto.InscripcionRequest;
import com.transportista.dto.InscripcionResponse;
import com.transportista.service.InscripcionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controlador REST para la gestión de inscripciones.
 *
 * Expone 3 endpoints REST:
 * 1. POST /api/inscripciones                 - Inscribir estudiante
 * 2. GET  /api/inscripciones                 - Consultar inscripciones por estudiante
 * 3. PUT  /api/inscripciones/{id}/calificar  - Calificar inscripción
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class InscripcionController {

    private final InscripcionService inscripcionService;

    // ================================================================
    // ENDPOINT 1: INSCRIBIR ESTUDIANTE
    // POST /api/inscripciones
    // Rol requerido: estudiante
    // ================================================================

    @PostMapping("/inscripciones")
    public ResponseEntity<InscripcionResponse> inscribirEstudiante(
            @Valid @RequestBody InscripcionRequest request) {
        log.info("POST /api/inscripciones — Inscribiendo estudiante: {}", request.getEstudiante());
        InscripcionResponse response = inscripcionService.inscribirEstudiante(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ================================================================
    // ENDPOINT 2: CONSULTAR INSCRIPCIONES POR ESTUDIANTE
    // GET /api/inscripciones?estudiante={e}
    // Rol requerido: estudiante
    // ================================================================

    @GetMapping("/inscripciones")
    public ResponseEntity<List<InscripcionResponse>> consultarInscripciones(
            @RequestParam(required = false) String estudiante) {
        log.info("GET /api/inscripciones — Consultando. Estudiante: {}", estudiante);
        List<InscripcionResponse> inscripciones = inscripcionService.consultarInscripciones(estudiante);
        return ResponseEntity.ok(inscripciones);
    }

    // ================================================================
    // ENDPOINT 3: CALIFICAR INSCRIPCIÓN
    // PUT /api/inscripciones/{id}/calificar
    // Rol requerido: instructor
    // ================================================================

    @PutMapping("/inscripciones/{id}/calificar")
    public ResponseEntity<InscripcionResponse> calificarInscripcion(
            @PathVariable Long id,
            @RequestParam Double calificacion) {
        log.info("PUT /api/inscripciones/{}/calificar — Calificando con nota: {}", id, calificacion);
        InscripcionResponse response = inscripcionService.calificarInscripcion(id, calificacion);
        return ResponseEntity.ok(response);
    }
}
