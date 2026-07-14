package com.transportista.controller;

import com.transportista.config.SecurityConfig;
import com.transportista.dto.GuiaDespachoRequest;
import com.transportista.dto.GuiaDespachoResponse;
import com.transportista.enums.EstadoGuia;
import com.transportista.service.GuiaDespachoService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests de integración del Controller (Criterio 1 + Criterio 3).
 *
 * Usa @WebMvcTest + MockMvc para probar los endpoints REST.
 * El Service se mockea para aislar la capa web.
 *
 * Criterio 3 (Spring Security):
 * - @WithMockUser simula autenticación JWT con roles
 * - Se prueban accesos con rol "admin" y rol "consulta"
 * - Se verifica que sin autenticación se recibe 401
 *
 * NOTA: app.security.enabled se activa (true) para testear
 * las reglas de seguridad con roles.
 */
@WebMvcTest(GuiaDespachoController.class)
@Import(SecurityConfig.class)
@ActiveProfiles("test")
@TestPropertySource(properties = "app.security.enabled=true")
@DisplayName("GuiaDespachoController — Tests de integración (con seguridad)")
class GuiaDespachoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GuiaDespachoService guiaService;

    @Autowired
    private ObjectMapper objectMapper;

    private GuiaDespachoRequest request;
    private GuiaDespachoResponse response;

    @BeforeEach
    void setUp() {
        objectMapper.registerModule(new JavaTimeModule());

        request = GuiaDespachoRequest.builder()
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.of(2025, 1, 15, 10, 30))
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .build();

        response = GuiaDespachoResponse.builder()
                .id(1L)
                .codigoGuia("GD-202501151030-001")
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.of(2025, 1, 15, 10, 30))
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .estado(EstadoGuia.ENVIADA)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // CRITERIO 3: Tests de seguridad (accesos por rol)
    // ================================================================

    @Nested
    @DisplayName("Seguridad — Control de acceso")
    class SeguridadTests {

        @Test
        @DisplayName("Sin autenticación → 401 (cuando app.security.enabled=true)")
        void sinAuth_debeRetornar401() throws Exception {
            mockMvc.perform(post("/api/guias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(csrf()))
                    .andExpect(status().isUnauthorized());  // 401
        }

        @Test
        @DisplayName("Con rol 'consulta' → NO puede crear guías (403)")
        @WithMockUser(authorities = "consulta")
        void rolConsulta_noPuedeCrearGuias() throws Exception {
            mockMvc.perform(post("/api/guias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(csrf()))
                    .andExpect(status().isForbidden());  // 403
        }

        @Test
        @DisplayName("Con rol 'consulta' → NO puede eliminar guías (403)")
        @WithMockUser(authorities = "consulta")
        void rolConsulta_noPuedeEliminarGuias() throws Exception {
            mockMvc.perform(delete("/api/guias/1")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con rol 'consulta' → NO puede subir a S3 (403)")
        @WithMockUser(authorities = "consulta")
        void rolConsulta_noPuedeSubirAS3() throws Exception {
            mockMvc.perform(post("/api/guias/1/subir-s3")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con rol 'consulta' → NO puede procesar cola (403)")
        @WithMockUser(authorities = "consulta")
        void rolConsulta_noPuedeProcesarCola() throws Exception {
            mockMvc.perform(post("/api/cola/procesar-guias")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("Con rol 'admin' → SÍ puede crear guías (201)")
        @WithMockUser(authorities = "admin")
        void rolAdmin_siPuedeCrearGuias() throws Exception {
            when(guiaService.crearGuia(any())).thenReturn(response);

            mockMvc.perform(post("/api/guias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(csrf()))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("Con rol 'admin' → NO puede descargar guías (403)")
        @WithMockUser(authorities = "admin")
        void rolAdmin_noPuedeDescargarGuias() throws Exception {
            // El endpoint de descarga es exclusivo del rol consulta
            mockMvc.perform(get("/api/guias/1/descargar")
                            .with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }

    // ================================================================
    // CRITERIO 1: Tests funcionales de endpoints (con rol admin)
    // ================================================================

    @Nested
    @DisplayName("CRUD Guías — Endpoints funcionales")
    @WithMockUser(authorities = "admin")
    class CrudEndpointsTests {

        @Test
        @DisplayName("POST /api/guias → 201 Created")
        void crearGuia_201() throws Exception {
            when(guiaService.crearGuia(any())).thenReturn(response);

            mockMvc.perform(post("/api/guias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(csrf()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.codigoGuia").value("GD-202501151030-001"))
                    .andExpect(jsonPath("$.transportista").value("Juan Perez"))
                    .andExpect(jsonPath("$.estado").value("ENVIADA"));
        }

        @Test
        @DisplayName("POST /api/guias sin campos obligatorios → 400 Bad Request")
        void crearGuia_sinCamposObligatorios_400() throws Exception {
            GuiaDespachoRequest invalido = new GuiaDespachoRequest(); // sin transportista, sin fecha

            mockMvc.perform(post("/api/guias")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(invalido))
                            .with(csrf()))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("PUT /api/guias/{id} → 200 OK")
        void modificarGuia_200() throws Exception {
            when(guiaService.modificarGuia(anyLong(), any())).thenReturn(response);

            mockMvc.perform(put("/api/guias/1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request))
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigoGuia").value("GD-202501151030-001"));
        }

        @Test
        @DisplayName("DELETE /api/guias/{id} → 204 No Content")
        void eliminarGuia_204() throws Exception {
            doNothing().when(guiaService).eliminarGuia(1L);

            mockMvc.perform(delete("/api/guias/1").with(csrf()))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("GET /api/guias?transportista=X&fecha=YYYY-MM-DD → 200 OK")
        void consultarGuias_200() throws Exception {
            when(guiaService.consultarGuias("Juan Perez", "2025-01-15"))
                    .thenReturn(List.of(response));

            mockMvc.perform(get("/api/guias")
                            .param("transportista", "Juan Perez")
                            .param("fecha", "2025-01-15")
                            .with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].codigoGuia").value("GD-202501151030-001"))
                    .andExpect(jsonPath("$[0].transportista").value("Juan Perez"));
        }

        @Test
        @DisplayName("GET /api/guias sin parámetros → 200 OK (todas)")
        void consultarGuias_sinParametros_200() throws Exception {
            when(guiaService.consultarGuias(null, null)).thenReturn(List.of(response));

            mockMvc.perform(get("/api/guias").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].id").value(1));
        }

        @Test
        @DisplayName("POST /api/guias/{id}/subir-s3 → 200 OK")
        void subirGuiaAS3_200() throws Exception {
            when(guiaService.subirGuiaAS3(1L)).thenReturn(response);

            mockMvc.perform(post("/api/guias/1/subir-s3").with(csrf()))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("POST /api/cola/procesar-guias → 200 OK")
        void procesarCola_200() throws Exception {
            when(guiaService.procesarColaYGuardar()).thenReturn(List.of());

            mockMvc.perform(post("/api/cola/procesar-guias").with(csrf()))
                    .andExpect(status().isOk());
        }
    }

    // ================================================================
    // Endpoint de descarga (rol consulta)
    // ================================================================
    @Nested
    @DisplayName("Descarga — Endpoint exclusivo rol consulta")
    @WithMockUser(authorities = "consulta")
    class DescargaEndpointTests {

        @Test
        @DisplayName("Rol consulta SÍ puede acceder a GET /api/guias/{id}/descargar → 200")
        void rolConsulta_siPuedeDescargar() throws Exception {
            when(guiaService.descargarGuia(1L)).thenReturn(response);

            mockMvc.perform(get("/api/guias/1/descargar").with(csrf()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.codigoGuia").value("GD-202501151030-001"));
        }

        @Test
        @DisplayName("Rol consulta tiene prohibido el resto de endpoints GET → 403")
        @WithMockUser(authorities = "consulta")
        void rolConsulta_noPuedeConsultarTodasLasGuias() throws Exception {
            mockMvc.perform(get("/api/guias").with(csrf()))
                    .andExpect(status().isForbidden());
        }
    }
}
