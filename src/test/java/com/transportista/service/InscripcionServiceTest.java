package com.transportista.service;

import com.transportista.dto.InscripcionRequest;
import com.transportista.dto.InscripcionResponse;
import com.transportista.entity.Curso;
import com.transportista.entity.Inscripcion;
import com.transportista.enums.EstadoCurso;
import com.transportista.enums.EstadoInscripcion;
import com.transportista.rabbitmq.RabbitMQConfig;
import com.transportista.repository.CursoRepository;
import com.transportista.repository.InscripcionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InscripcionService - Unit Tests")
class InscripcionServiceTest {

    @Mock
    private InscripcionRepository inscripcionRepository;

    @Mock
    private CursoRepository cursoRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private InscripcionService inscripcionService;

    private InscripcionRequest crearInscripcionRequest() {
        return InscripcionRequest.builder()
                .codigoCurso("CUR-202501151030-001")
                .estudiante("Juan Perez")
                .emailEstudiante("juan.perez@test.com")
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
                .fechaInicio(LocalDateTime.of(2025, 1, 15, 10, 30))
                .estado(EstadoCurso.PUBLICADO)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    private Inscripcion crearInscripcion() {
        return Inscripcion.builder()
                .id(1L)
                .codigoCurso("CUR-202501151030-001")
                .estudiante("Juan Perez")
                .emailEstudiante("juan.perez@test.com")
                .fechaInscripcion(LocalDateTime.now())
                .estado(EstadoInscripcion.PENDIENTE)
                .build();
    }

    @Test
    @DisplayName("inscribirEstudiante: valida curso, guarda con PENDIENTE y envia a RabbitMQ")
    void inscribirEstudiante() {
        InscripcionRequest request = crearInscripcionRequest();
        Curso curso = crearCurso();
        Inscripcion inscripcionGuardada = crearInscripcion();
        when(cursoRepository.findByCodigoCurso("CUR-202501151030-001")).thenReturn(Optional.of(curso));
        when(inscripcionRepository.save(any(Inscripcion.class))).thenReturn(inscripcionGuardada);

        InscripcionResponse response = inscripcionService.inscribirEstudiante(request);

        verify(cursoRepository, times(1)).findByCodigoCurso("CUR-202501151030-001");
        verify(inscripcionRepository, times(1)).save(any(Inscripcion.class));
        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_PRINCIPAL,
                inscripcionGuardada);
        assertNotNull(response);
        assertEquals("PENDIENTE", response.getEstado());
        assertEquals("Juan Perez", response.getEstudiante());
    }

    @Test
    @DisplayName("inscribirEstudiante_cursoNoExiste: lanza excepcion")
    void inscribirEstudiante_cursoNoExiste() {
        InscripcionRequest request = crearInscripcionRequest();
        when(cursoRepository.findByCodigoCurso("CUR-202501151030-001")).thenReturn(Optional.empty());

        RuntimeException exception = assertThrows(RuntimeException.class,
                () -> inscripcionService.inscribirEstudiante(request));
        assertTrue(exception.getMessage().contains("Curso no encontrado"));
        verify(inscripcionRepository, never()).save(any(Inscripcion.class));
        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(Inscripcion.class));
    }

    @Test
    @DisplayName("consultarInscripciones: filtra por estudiante")
    void consultarInscripciones() {
        Inscripcion inscripcion = crearInscripcion();
        when(inscripcionRepository.findByEstudiante("Juan Perez")).thenReturn(List.of(inscripcion));

        List<InscripcionResponse> resultados = inscripcionService.consultarInscripciones("Juan Perez");

        assertNotNull(resultados);
        assertEquals(1, resultados.size());
        assertEquals("Juan Perez", resultados.get(0).getEstudiante());
        assertEquals("CUR-202501151030-001", resultados.get(0).getCodigoCurso());
    }

    @Test
    @DisplayName("calificarInscripcion: establece calificacion y estado CALIFICADO")
    void calificarInscripcion() {
        Inscripcion inscripcion = crearInscripcion();
        when(inscripcionRepository.findById(1L)).thenReturn(Optional.of(inscripcion));
        when(inscripcionRepository.save(any(Inscripcion.class))).thenReturn(inscripcion);

        InscripcionResponse response = inscripcionService.calificarInscripcion(1L, 9.5);

        verify(inscripcionRepository, times(1)).findById(1L);
        verify(inscripcionRepository, times(1)).save(inscripcion);
        assertNotNull(response);
        assertEquals(9.5, response.getCalificacion());
        assertEquals("CALIFICADO", response.getEstado());
    }
}
