package com.transportista.controller;

import com.transportista.dto.CursoRequest;
import com.transportista.dto.CursoResponse;
import com.transportista.service.CursoService;
import com.transportista.service.InscripcionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CursoController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = {"app.security.enabled=false"})
@DisplayName("CursoController — Endpoints REST")
class CursoControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CursoService cursoService;
    @MockBean private InscripcionService inscripcionService;

    private CursoRequest request;
    private CursoResponse response;

    @BeforeEach
    void setUp() {
        request = new CursoRequest();
        request.setNombre("Curso de Java");
        request.setInstructor("Juan Perez");
        request.setDescripcion("Curso avanzado");
        request.setCreditos(5.0);
        request.setFechaInicio(LocalDateTime.of(2025, 1, 15, 10, 30));
        request.setFechaFin(LocalDateTime.of(2025, 6, 15, 18, 0));

        response = CursoResponse.builder()
                .id(1L)
                .codigoCurso("CUR-202501151030-001")
                .nombre("Curso de Java")
                .instructor("Juan Perez")
                .descripcion("Curso avanzado")
                .creditos(5.0)
                .fechaInicio(LocalDateTime.of(2025, 1, 15, 10, 30))
                .fechaFin(LocalDateTime.of(2025, 6, 15, 18, 0))
                .estado("PENDIENTE")
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test @DisplayName("POST /api/cursos → 201 Created")
    void crearCurso() throws Exception {
        when(cursoService.crearCurso(any())).thenReturn(response);
        mockMvc.perform(post("/api/cursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigoCurso").value("CUR-202501151030-001"));
    }

    @Test @DisplayName("PUT /api/cursos/{id} → 200 OK")
    void modificarCurso() throws Exception {
        when(cursoService.modificarCurso(eq(1L), any())).thenReturn(response);
        mockMvc.perform(put("/api/cursos/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("DELETE /api/cursos/{id} → 204 No Content")
    void eliminarCurso() throws Exception {
        mockMvc.perform(delete("/api/cursos/1"))
                .andExpect(status().isNoContent());
    }

    @Test @DisplayName("GET /api/cursos con filtros → 200 OK")
    void consultarCursos() throws Exception {
        when(cursoService.consultarCursos("Juan Perez", "2025-01-15"))
                .thenReturn(List.of(response));
        mockMvc.perform(get("/api/cursos")
                .param("instructor", "Juan Perez")
                .param("fecha", "2025-01-15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].codigoCurso").value("CUR-202501151030-001"));
    }

    @Test @DisplayName("POST /api/cursos/{id}/subir-s3 → 200 OK")
    void subirCursoAS3() throws Exception {
        when(cursoService.subirCursoAS3(1L)).thenReturn(response);
        mockMvc.perform(post("/api/cursos/1/subir-s3"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/cursos/{id}/descargar → 200 OK")
    void descargarCurso() throws Exception {
        when(cursoService.descargarCurso(1L)).thenReturn(response);
        mockMvc.perform(get("/api/cursos/1/descargar"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST con datos inválidos → 400 Bad Request")
    void validacionFalla() throws Exception {
        CursoRequest invalid = new CursoRequest();
        mockMvc.perform(post("/api/cursos")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest());
    }

    @Test @DisplayName("GET /api/cola/consumir-mensaje → 200 OK")
    void consumirMensaje() throws Exception {
        when(cursoService.consumirMensajeDeCola()).thenReturn(response);
        mockMvc.perform(get("/api/cola/consumir-mensaje"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("POST /api/cola/procesar-inscripciones → 200 OK")
    void procesarInscripciones() throws Exception {
        when(inscripcionService.procesarColaYGuardar()).thenReturn(List.of());
        mockMvc.perform(post("/api/cola/procesar-inscripciones"))
                .andExpect(status().isOk());
    }

    @Test @DisplayName("GET /api/cursos sin filtros → 200 OK")
    void consultarCursosSinFiltros() throws Exception {
        when(cursoService.consultarCursos(null, null)).thenReturn(List.of(response));
        mockMvc.perform(get("/api/cursos"))
                .andExpect(status().isOk());
    }
}
