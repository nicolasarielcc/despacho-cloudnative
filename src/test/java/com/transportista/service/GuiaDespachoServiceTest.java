package com.transportista.service;

import com.transportista.dto.GuiaDespachoRequest;
import com.transportista.dto.GuiaDespachoResponse;
import com.transportista.entity.GuiaDespacho;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.enums.EstadoGuia;
import com.transportista.rabbitmq.GuiaProducer;
import com.transportista.repository.GuiaDespachoProcesadaRepository;
import com.transportista.repository.GuiaDespachoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios de GuiaDespachoService.
 * Verifica toda la lógica de negocio con dependencias mockeadas.
 *
 * Criterios cubiertos:
 * - Criterio 1: CRUD de guías de despacho (crear, modificar, eliminar, consultar)
 * - Criterio 2: Enrutamiento a colas RabbitMQ (cola 1 éxito, cola 2 error)
 * - Criterio 3: Preparación para roles (el servicio no maneja seguridad;
 *               eso lo hace el Controller y SecurityConfig)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaDespachoService — Lógica de negocio")
class GuiaDespachoServiceTest {

    @Mock
    private GuiaDespachoRepository guiaRepository;

    @Mock
    private GuiaDespachoProcesadaRepository procesadaRepository;

    @Mock
    private GuiaProducer guiaProducer;

    @InjectMocks
    private GuiaDespachoService service;

    private GuiaDespachoRequest request;
    private GuiaDespacho guiaGuardada;

    @BeforeEach
    void setUp() {
        request = GuiaDespachoRequest.builder()
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.of(2025, 1, 15, 10, 30))
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .build();

        guiaGuardada = GuiaDespacho.builder()
                .id(1L)
                .codigoGuia("GD-202501151030-001")
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.of(2025, 1, 15, 10, 30))
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .estado(EstadoGuia.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    // ================================================================
    // CREAR GUÍA (Criterio 1 + Criterio 2)
    // ================================================================
    @Nested
    @DisplayName("crearGuia()")
    class CrearGuiaTests {

        @Test
        @DisplayName("Guarda en BD y envía a cola principal cuando todo funciona")
        void debeGuardarYEnviarAColaExitosa() {
            when(guiaRepository.save(any(GuiaDespacho.class))).thenReturn(guiaGuardada);
            doNothing().when(guiaProducer).enviarAColaPrincipal(any());

            GuiaDespachoResponse response = service.crearGuia(request);

            verify(guiaRepository, times(1)).save(any(GuiaDespacho.class));
            verify(guiaProducer, times(1)).enviarAColaPrincipal(any());

            assertEquals(EstadoGuia.PENDIENTE, response.getEstado());
            assertEquals("Juan Perez", response.getTransportista());
            assertEquals("Santiago", response.getOrigen());
            assertEquals("Valparaíso", response.getDestino());
            assertEquals(150.0, response.getPesoKg());
            assertNotNull(response.getCodigoGuia());
        }

        @Test
        @DisplayName("Si falla el envío a cola, la excepción se propaga")
        void siFallaEnvio_propagaExcepcion() {
            when(guiaRepository.save(any(GuiaDespacho.class))).thenReturn(guiaGuardada);
            doThrow(new RuntimeException("RabbitMQ caído"))
                    .when(guiaProducer).enviarAColaPrincipal(any());

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> service.crearGuia(request));

            assertEquals("RabbitMQ caído", ex.getMessage());
            verify(guiaProducer, times(1)).enviarAColaPrincipal(any());
        }

        @Test
        @DisplayName("El código de guía se genera automáticamente con formato GD-*")
        void debeGenerarCodigoGuiaAutomaticamente() {
            when(guiaRepository.save(any(GuiaDespacho.class))).thenReturn(guiaGuardada);
            doNothing().when(guiaProducer).enviarAColaPrincipal(any());

            GuiaDespachoResponse response = service.crearGuia(request);

            assertNotNull(response.getCodigoGuia());
            assertTrue(response.getCodigoGuia().startsWith("GD-"),
                    "El código debe empezar con GD-");
            assertTrue(response.getCodigoGuia().matches("GD-\\d{12}-\\d{3}"),
                    "Formato esperado: GD-{timestamp12digitos}-{secuencial3digitos}");
        }
    }

    // ================================================================
    // MODIFICAR GUÍA (Criterio 1)
    // ================================================================
    @Nested
    @DisplayName("modificarGuia()")
    class ModificarGuiaTests {

        @Test
        @DisplayName("Modifica todos los campos y reenvía a la cola exitosa")
        void debeModificarYReenviar() {
            when(guiaRepository.findById(1L)).thenReturn(Optional.of(guiaGuardada));
            when(guiaRepository.save(any(GuiaDespacho.class))).thenReturn(guiaGuardada);
            doNothing().when(guiaProducer).enviarAColaPrincipal(any());

            GuiaDespachoRequest nuevosDatos = GuiaDespachoRequest.builder()
                    .transportista("Maria Lopez")
                    .fechaEmision(LocalDateTime.of(2025, 2, 20, 15, 0))
                    .origen("Concepción")
                    .destino("Temuco")
                    .descripcionCarga("Muebles")
                    .pesoKg(300.0)
                    .build();

            GuiaDespachoResponse response = service.modificarGuia(1L, nuevosDatos);

            verify(guiaProducer, times(1)).enviarAColaPrincipal(any());
            assertEquals("Maria Lopez", response.getTransportista());
            assertEquals("Concepción", response.getOrigen());
            assertEquals("Temuco", response.getDestino());
            assertEquals("Muebles", response.getDescripcionCarga());
            assertEquals(300.0, response.getPesoKg());
        }

        @Test
        @DisplayName("Lanza excepción si la guía no existe")
        void lanzaExcepcionSiGuiaNoExiste() {
            when(guiaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> service.modificarGuia(999L, request));
        }
    }

    // ================================================================
    // ELIMINAR GUÍA (Criterio 1)
    // ================================================================
    @Nested
    @DisplayName("eliminarGuia()")
    class EliminarGuiaTests {

        @Test
        @DisplayName("Elimina correctamente una guía existente")
        void debeEliminarCorrectamente() {
            when(guiaRepository.findById(1L)).thenReturn(Optional.of(guiaGuardada));

            service.eliminarGuia(1L);

            verify(guiaRepository, times(1)).delete(guiaGuardada);
        }

        @Test
        @DisplayName("Lanza excepción si la guía no existe")
        void lanzaExcepcionSiNoExiste() {
            when(guiaRepository.findById(999L)).thenReturn(Optional.empty());

            assertThrows(RuntimeException.class,
                    () -> service.eliminarGuia(999L));
        }
    }

    // ================================================================
    // CONSULTAR GUÍAS (Criterio 1)
    // ================================================================
    @Nested
    @DisplayName("consultarGuias()")
    class ConsultarGuiasTests {

        @Test
        @DisplayName("Filtra por transportista y fecha correctamente")
        void debeFiltrarPorTransportistaYFecha() {
            LocalDateTime inicio = LocalDateTime.of(2025, 1, 15, 0, 0);
            LocalDateTime fin = LocalDateTime.of(2025, 1, 15, 23, 59, 59);
            when(guiaRepository.findByTransportistaAndFechaEmisionBetween(
                    "Juan Perez", inicio, fin)).thenReturn(List.of(guiaGuardada));

            List<GuiaDespachoResponse> resultado = service.consultarGuias("Juan Perez", "2025-01-15");

            assertEquals(1, resultado.size());
            assertEquals("GD-202501151030-001", resultado.get(0).getCodigoGuia());
        }

        @Test
        @DisplayName("Devuelve lista vacía si no hay resultados")
        void devuelveVacioSiNoHayResultados() {
            when(guiaRepository.findByTransportistaAndFechaEmisionBetween(
                    any(), any(), any())).thenReturn(List.of());

            List<GuiaDespachoResponse> resultado = service.consultarGuias("Nadie", "2025-06-15");

            assertTrue(resultado.isEmpty());
        }
    }

    // ================================================================
    // PROCESAR COLA 1 (Criterio 2 — endpoint adicional)
    // ================================================================
    @Nested
    @DisplayName("procesarColaYGuardar()")
    class ProcesarColaTests {

        @Test
        @DisplayName("Procesa guías ENVIADA y las guarda en tabla NUEVA")
        void debeProcesarGuiasEnviadas_yGuardarEnTablaNueva() {
            guiaGuardada.setEstado(EstadoGuia.ENVIADA);
            when(guiaRepository.findByEstado(EstadoGuia.ENVIADA))
                    .thenReturn(List.of(guiaGuardada));
            when(procesadaRepository.save(any(GuiaDespachoProcesada.class)))
                    .thenReturn(new GuiaDespachoProcesada());

            List<GuiaDespachoProcesada> resultado = service.procesarColaYGuardar();

            assertEquals(1, resultado.size());
            verify(procesadaRepository, times(1)).save(any(GuiaDespachoProcesada.class));
        }

        @Test
        @DisplayName("No procesa nada si no hay guías ENVIADA")
        void debeRetornarVacioSiNoHayGuiasEnviadas() {
            when(guiaRepository.findByEstado(EstadoGuia.ENVIADA)).thenReturn(List.of());

            List<GuiaDespachoProcesada> resultado = service.procesarColaYGuardar();

            assertTrue(resultado.isEmpty());
            verify(procesadaRepository, never()).save(any());
        }
    }
}
