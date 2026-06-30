package com.duoc.despacho_service.controller;

import com.duoc.despacho_service.dto.request.GuiaRequestDTO;
import com.duoc.despacho_service.dto.response.GuiaResponseDTO;
import com.duoc.despacho_service.service.GuiaService;
import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/guias")
public class GuiaController {

    private final GuiaService guiaService;

    public GuiaController(GuiaService guiaService) {
        this.guiaService = guiaService;
    }

    // 1. Crear guías de despacho
    @PostMapping
    public ResponseEntity<GuiaResponseDTO> crearGuia(@Valid @RequestBody GuiaRequestDTO guia) {
        return ResponseEntity.ok(guiaService.crearGuiaTemporal(guia));
    }

    // 2. Subir guías generadas a S3
    @PostMapping("/{id}/subir")
    public ResponseEntity<GuiaResponseDTO> subirAS3(@PathVariable Long id) {
        return ResponseEntity.ok(guiaService.subirAS3(id));
    }

    // 3. Descargar guías con validación de permisos
    @GetMapping("/{id}/descargar")
    public ResponseEntity<byte[]> descargarGuia(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {

        // El claim extension_rolDespacho contiene "descarga" o "gestion"
        String rol = jwt.getClaimAsString("extension_rolDespacho");
        byte[] contenido = guiaService.descargarGuiaConPermisos(id, rol);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=guia-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(contenido);
    }

    // 4. Modificar o actualizar guías
    @PutMapping("/{id}")
    public ResponseEntity<GuiaResponseDTO> actualizarGuia(@PathVariable Long id, @Valid @RequestBody GuiaRequestDTO guia) {
        return ResponseEntity.ok(guiaService.actualizarGuia(id, guia));
    }

    // 5. Eliminar guías específicas
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarGuia(@PathVariable Long id) {
        guiaService.eliminarGuia(id);
        return ResponseEntity.noContent().build();
    }

    // 6. Consultar guías por transportista y fecha
    @GetMapping("/buscar")
    public ResponseEntity<List<GuiaResponseDTO>> consultarGuias(
            @RequestParam String transportista,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha) {
        return ResponseEntity.ok(guiaService.buscarPorTransportistaYFecha(transportista, fecha));
    }
}