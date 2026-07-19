package com.transportista.rabbitmq;

import com.transportista.entity.Inscripcion;
import com.transportista.enums.EstadoInscripcion;
import com.transportista.repository.CursoRepository;
import com.transportista.repository.InscripcionRepository;
import com.transportista.service.S3Service;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("CursoConsumer - Unit Tests")
class CursoConsumerTest {

    @Mock
    private InscripcionRepository inscripcionRepository;

    @Mock
    private CursoRepository cursoRepository;

    @Mock
    private S3Service s3Service;

    @InjectMocks
    private CursoConsumer cursoConsumer;

    private Inscripcion crearInscripcion() {
        return Inscripcion.builder()
                .id(1L)
                .codigoCurso("CUR-202501151030-001")
                .estudiante("Juan Perez")
                .emailEstudiante("juan.perez@test.com")
                .fechaInscripcion(LocalDateTime.of(2025, 1, 15, 10, 30))
                .estado(EstadoInscripcion.PENDIENTE)
                .build();
    }

    @Test
    @DisplayName("procesarInscripcion: guarda Inscripcion con estado INSCRITO y verifica campos")
    void procesarInscripcion() {
        Inscripcion inscripcion = crearInscripcion();
        when(s3Service.subirGuia(anyString(), anyString(), any(byte[].class)))
                .thenReturn("https://test-bucket.s3.amazonaws.com/cursos/juan_perez/2025/01/curso-CUR-202501151030-001.pdf");
        when(inscripcionRepository.save(any(Inscripcion.class))).thenReturn(inscripcion);

        cursoConsumer.procesarInscripcion(inscripcion);

        verify(s3Service, times(1)).subirGuia(eq("Juan Perez"), eq("CUR-202501151030-001"), any(byte[].class));
        verify(inscripcionRepository, times(1)).save(inscripcion);
        assertEquals(EstadoInscripcion.INSCRITO, inscripcion.getEstado());
        assertNotNull(inscripcion.getFechaProcesamiento());
    }

    @Test
    @DisplayName("procesarDlq: guarda con estado CON_ERROR")
    void procesarDlq() {
        Inscripcion inscripcion = crearInscripcion();

        cursoConsumer.procesarDlq(inscripcion);

        verify(inscripcionRepository, times(1)).save(inscripcion);
        assertEquals(EstadoInscripcion.CON_ERROR, inscripcion.getEstado());
    }

    @Test
    @DisplayName("siFallaS3_lanzaExcepcion: S3Service.subirGuia lanza RuntimeException")
    void siFallaS3_lanzaExcepcion() {
        Inscripcion inscripcion = crearInscripcion();
        when(s3Service.subirGuia(anyString(), anyString(), any(byte[].class)))
                .thenThrow(new RuntimeException("Error al subir a S3"));

        assertThrows(RuntimeException.class, () -> cursoConsumer.procesarInscripcion(inscripcion));
        verify(inscripcionRepository, never()).save(any(Inscripcion.class));
    }
}
