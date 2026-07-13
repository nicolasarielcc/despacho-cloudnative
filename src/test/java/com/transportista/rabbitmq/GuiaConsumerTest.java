package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.enums.EstadoGuia;
import com.transportista.repository.GuiaDespachoProcesadaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para GuiaConsumer (Criterio 2 — RabbitMQ).
 *
 * Verifica que:
 * 1. procesarGuia() guarda el mensaje en GUIA_DESPACHO_PROCESADA
 * 2. procesarDlq() registra el error sin lanzar excepción
 * 3. Los mensajes de error NO se guardan en la tabla de procesadas
 * 4. Si el guardado falla, se lanza RuntimeException (para DLX)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaConsumer — Consumo de mensajes RabbitMQ")
class GuiaConsumerTest {

    @Mock
    private GuiaDespachoProcesadaRepository procesadaRepository;

    @InjectMocks
    private GuiaConsumer guiaConsumer;

    private GuiaDespacho guiaTest;

    @BeforeEach
    void setUp() {
        guiaTest = GuiaDespacho.builder()
                .id(1L)
                .codigoGuia("GD-202501151030-001")
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.of(2025, 1, 15, 10, 30))
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .estado(EstadoGuia.ENVIADA)
                .urlS3("s3://bucket/guias/juan_perez/2025/guia-GD-202501151030-001.pdf")
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("procesarGuia: guarda en GUIA_DESPACHO_PROCESADA")
    void procesarGuia_debeGuardarEnTablaProcesada() {
        GuiaDespachoProcesada procesadaMock = new GuiaDespachoProcesada();
        when(procesadaRepository.save(any(GuiaDespachoProcesada.class))).thenReturn(procesadaMock);

        guiaConsumer.procesarGuia(guiaTest);

        ArgumentCaptor<GuiaDespachoProcesada> captor = ArgumentCaptor.forClass(GuiaDespachoProcesada.class);
        verify(procesadaRepository, times(1)).save(captor.capture());

        GuiaDespachoProcesada guardada = captor.getValue();
        assertEquals("GD-202501151030-001", guardada.getCodigoGuia());
        assertEquals("Juan Perez", guardada.getTransportista());
        assertEquals("Santiago", guardada.getOrigen());
        assertEquals("Valparaíso", guardada.getDestino());
        assertEquals("Electrodomésticos", guardada.getDescripcionCarga());
        assertEquals(150.0, guardada.getPesoKg());
        assertEquals(EstadoGuia.ENVIADA, guardada.getEstado());
        assertNotNull(guardada.getFechaProcesamiento(), "Debe asignar fechaDeProcesamiento");
    }

    @Test
    @DisplayName("procesarGuia: asigna fechaProcesamiento al momento actual")
    void procesarGuia_debeAsignarFechaProcesamiento() {
        GuiaDespachoProcesada procesadaMock = new GuiaDespachoProcesada();
        when(procesadaRepository.save(any(GuiaDespachoProcesada.class))).thenReturn(procesadaMock);

        guiaConsumer.procesarGuia(guiaTest);

        ArgumentCaptor<GuiaDespachoProcesada> captor = ArgumentCaptor.forClass(GuiaDespachoProcesada.class);
        verify(procesadaRepository).save(captor.capture());

        // La fecha debe ser cercana a ahora
        LocalDateTime ahora = LocalDateTime.now();
        LocalDateTime fechaGuardada = captor.getValue().getFechaProcesamiento();
        assertNotNull(fechaGuardada);
        assertTrue(Math.abs(java.time.Duration.between(ahora, fechaGuardada).getSeconds()) < 5,
                "Fecha de procesamiento debe ser ~ ahora (±5s)");
    }

    @Test
    @DisplayName("procesarGuia: si falla DB, lanza RuntimeException (activa DLX)")
    void procesarGuia_siFallaGuardado_debeLanzarExcepcion() {
        when(procesadaRepository.save(any())).thenThrow(new RuntimeException("DB error"));

        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                guiaConsumer.procesarGuia(guiaTest));

        assertTrue(ex.getMessage().contains("GD-202501151030-001"),
                "El mensaje de error debe contener el código de guía");
    }

    @Test
    @DisplayName("procesarDlq: NO guarda en tabla procesada (solo log)")
    void procesarDlq_noDebeGuardarEnBD() {
        guiaConsumer.procesarDlq(guiaTest);

        verifyNoInteractions(procesadaRepository);
    }

    @Test
    @DisplayName("procesarDlq: no lanza excepción (el mensaje queda en la cola)")
    void procesarDlq_noDebeLanzarExcepcion() {
        // No debe lanzar excepción al consumir mensaje de error
        assertDoesNotThrow(() -> guiaConsumer.procesarDlq(guiaTest));
    }

    @Test
    @DisplayName("procesarGuia: usa la misma URL S3 del mensaje original")
    void procesarGuia_debeConservarUrlS3() {
        GuiaDespachoProcesada procesadaMock = new GuiaDespachoProcesada();
        when(procesadaRepository.save(any())).thenReturn(procesadaMock);

        guiaConsumer.procesarGuia(guiaTest);

        ArgumentCaptor<GuiaDespachoProcesada> captor = ArgumentCaptor.forClass(GuiaDespachoProcesada.class);
        verify(procesadaRepository).save(captor.capture());

        assertEquals("s3://bucket/guias/juan_perez/2025/guia-GD-202501151030-001.pdf",
                captor.getValue().getUrlS3());
    }
}
