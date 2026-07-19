package com.transportista.service;

import com.transportista.dto.CursoRequest;
import com.transportista.dto.CursoResponse;
import com.transportista.entity.Curso;
import com.transportista.enums.EstadoCurso;
import com.transportista.rabbitmq.CursoProducer;
import com.transportista.repository.CursoRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CursoService - Unit Tests")
class CursoServiceTest {

    @Mock
    private CursoRepository cursoRepository;

    @Mock
    private CursoProducer cursoProducer;

    @InjectMocks
    private CursoService cursoService;

    private final LocalDateTime fechaInicio = LocalDateTime.of(2025, 1, 15, 10, 30);

    private CursoRequest crearCursoRequest() {
        return CursoRequest.builder()
                .nombre("Curso de Java")
                .instructor("Juan Perez")
                .descripcion("Curso avanzado de Java")
                .creditos(5.0)
                .fechaInicio(fechaInicio)
                .build();
    }

    private Curso crearCurso() {
        return Curso.builder()
                .id(1L)
                .codigoCurso("CUR-202501151030-001")
                .nombre("Curso de Java")
                .instructor("Juan Perez")
                .descripcion("Curso avanzado de Java")
                .creditos(5.0)
                .fechaInicio(fechaInicio)
                .estado(EstadoCurso.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Nested
    @DisplayName("CrearCurso")
    class CrearCursoTests {

        @Test
        @DisplayName("debeGuardarYEnviarACola")
        void debeGuardarYEnviarACola() {
            CursoRequest request = crearCursoRequest();
            Curso cursoGuardado = crearCurso();
            when(cursoRepository.save(any(Curso.class))).thenReturn(cursoGuardado);

            CursoResponse response = cursoService.crearCurso(request);

            verify(cursoRepository, times(1)).save(any(Curso.class));
            verify(cursoProducer, times(1)).enviarAColaPrincipal(any(Curso.class));
            assertNotNull(response);
            assertEquals(EstadoCurso.PENDIENTE.name(), response.getEstado());
            assertNotNull(response.getCodigoCurso());
            assertTrue(response.getCodigoCurso().startsWith("CUR-"),
                    "El codigoCurso debe comenzar con 'CUR-'");
        }

        @Test
        @DisplayName("siFallaEnvio_propagaExcepcion")
        void siFallaEnvio_propagaExcepcion() {
            CursoRequest request = crearCursoRequest();
            Curso cursoGuardado = crearCurso();
            when(cursoRepository.save(any(Curso.class))).thenReturn(cursoGuardado);
            doThrow(new RuntimeException("Error de RabbitMQ")).when(cursoProducer).enviarAColaPrincipal(any(Curso.class));

            RuntimeException exception = assertThrows(RuntimeException.class, () -> cursoService.crearCurso(request));
            assertEquals("Error de RabbitMQ", exception.getMessage());
            verify(cursoRepository, times(1)).save(any(Curso.class));
            verify(cursoProducer, times(1)).enviarAColaPrincipal(any(Curso.class));
        }

        @Test
        @DisplayName("debeGenerarCodigoCursoAutomaticamente")
        void debeGenerarCodigoCursoAutomaticamente() {
            CursoRequest request = crearCursoRequest();
            when(cursoRepository.save(any(Curso.class))).thenAnswer(invocation -> {
                Curso c = invocation.getArgument(0);
                c.setId(1L);
                return c;
            });

            CursoResponse response = cursoService.crearCurso(request);

            assertNotNull(response.getCodigoCurso());
            assertTrue(response.getCodigoCurso().matches("CUR-\\d{12}-\\d{3}"),
                    "El codigoCurso debe tener el formato CUR-{12digits}-{3digits}, actual: " + response.getCodigoCurso());
        }
    }

    @Nested
    @DisplayName("ModificarCurso")
    class ModificarCursoTests {

        @Test
        @DisplayName("debeModificarYReenviar")
        void debeModificarYReenviar() {
            Curso cursoExistente = crearCurso();
            cursoExistente.setEstado(EstadoCurso.PUBLICADO);
            CursoRequest request = CursoRequest.builder()
                    .nombre("Curso de Java Actualizado")
                    .instructor("Juan Perez")
                    .descripcion("Curso avanzado de Java - Edicion 2025")
                    .creditos(6.0)
                    .fechaInicio(fechaInicio)
                    .build();
            when(cursoRepository.findById(1L)).thenReturn(Optional.of(cursoExistente));
            when(cursoRepository.save(any(Curso.class))).thenReturn(cursoExistente);

            CursoResponse response = cursoService.modificarCurso(1L, request);

            verify(cursoRepository, times(1)).findById(1L);
            verify(cursoRepository, times(1)).save(any(Curso.class));
            verify(cursoProducer, times(1)).enviarAColaPrincipal(any(Curso.class));
            assertNotNull(response);
            assertEquals("Curso de Java Actualizado", response.getNombre());
        }

        @Test
        @DisplayName("lanzaExcepcionSiCursoNoExiste")
        void lanzaExcepcionSiCursoNoExiste() {
            CursoRequest request = crearCursoRequest();
            when(cursoRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> cursoService.modificarCurso(99L, request));
            assertTrue(exception.getMessage().contains("Curso no encontrado"));
            verify(cursoRepository, times(1)).findById(99L);
            verify(cursoRepository, never()).save(any(Curso.class));
            verify(cursoProducer, never()).enviarAColaPrincipal(any(Curso.class));
        }
    }

    @Nested
    @DisplayName("EliminarCurso")
    class EliminarCursoTests {

        @Test
        @DisplayName("debeEliminarCorrectamente")
        void debeEliminarCorrectamente() {
            Curso cursoExistente = crearCurso();
            when(cursoRepository.findById(1L)).thenReturn(Optional.of(cursoExistente));

            cursoService.eliminarCurso(1L);

            verify(cursoRepository, times(1)).findById(1L);
            verify(cursoRepository, times(1)).delete(cursoExistente);
        }

        @Test
        @DisplayName("lanzaExcepcionSiNoExiste")
        void lanzaExcepcionSiNoExiste() {
            when(cursoRepository.findById(99L)).thenReturn(Optional.empty());

            RuntimeException exception = assertThrows(RuntimeException.class, () -> cursoService.eliminarCurso(99L));
            assertTrue(exception.getMessage().contains("Curso no encontrado"));
            verify(cursoRepository, times(1)).findById(99L);
            verify(cursoRepository, never()).delete(any(Curso.class));
        }
    }

    @Nested
    @DisplayName("ConsultarCursos")
    class ConsultarCursosTests {

        @Test
        @DisplayName("debeFiltrarPorInstructorYFecha")
        void debeFiltrarPorInstructorYFecha() {
            Curso curso = crearCurso();
            when(cursoRepository.findByInstructorAndFechaInicioBetween(
                    eq("Juan Perez"), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of(curso));

            List<CursoResponse> resultados = cursoService.consultarCursos("Juan Perez", "2025-01-15");

            assertNotNull(resultados);
            assertEquals(1, resultados.size());
            assertEquals("Curso de Java", resultados.get(0).getNombre());
            assertEquals("Juan Perez", resultados.get(0).getInstructor());
        }

        @Test
        @DisplayName("devuelveVacioSiNoHayResultados")
        void devuelveVacioSiNoHayResultados() {
            when(cursoRepository.findByInstructorAndFechaInicioBetween(
                    eq("Instructor Inexistente"), any(LocalDateTime.class), any(LocalDateTime.class)))
                    .thenReturn(List.of());

            List<CursoResponse> resultados = cursoService.consultarCursos("Instructor Inexistente", "2025-01-15");

            assertNotNull(resultados);
            assertTrue(resultados.isEmpty());
        }
    }
}
