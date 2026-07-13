package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import com.transportista.enums.EstadoGuia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitarios para GuiaProducer (Criterio 2 — RabbitMQ).
 *
 * Verifica que:
 * 1. enviarAColaPrincipal() envía a la cola correcta con la routing key correcta
 * 2. enviarAColaPrincipal() envía a la cola correcta con la routing key correcta
 * 3. Los mensajes se serializan como objetos GuiaDespacho
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("GuiaProducer — Envío de mensajes a RabbitMQ")
class GuiaProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private GuiaProducer guiaProducer;

    private GuiaDespacho guiaTest;

    @BeforeEach
    void setUp() {
        guiaTest = GuiaDespacho.builder()
                .id(1L)
                .codigoGuia("GD-202501151030-001")
                .transportista("Juan Perez")
                .fechaEmision(LocalDateTime.now())
                .origen("Santiago")
                .destino("Valparaíso")
                .descripcionCarga("Electrodomésticos")
                .pesoKg(150.0)
                .estado(EstadoGuia.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("enviarAColaPrincipal: usa exchange-guias y routing key guia.exitosa")
    void enviarAColaPrincipal_debeUsarExchangeYRkCorrectos() {
        // Ejecutar
        guiaProducer.enviarAColaPrincipal(guiaTest);

        // Verificar que se llamó a convertAndSend con los parámetros correctos
        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                routingKeyCaptor.capture(),
                eq(guiaTest));

        assertEquals(RabbitMQConfig.ROUTING_KEY_PRINCIPAL, routingKeyCaptor.getValue(),
                "La routing key debe ser 'guia.exitosa'");
    }

    @Test
    @DisplayName("enviarAColaPrincipal: envía el mismo objeto GuiaDespacho que recibe")
    void enviarAColaPrincipal_debeEnviarElMismoObjeto() {
        guiaProducer.enviarAColaPrincipal(guiaTest);

        ArgumentCaptor<GuiaDespacho> guiaCaptor = ArgumentCaptor.forClass(GuiaDespacho.class);
        verify(rabbitTemplate).convertAndSend(
                anyString(), anyString(), guiaCaptor.capture());

        GuiaDespacho enviada = guiaCaptor.getValue();
        assertEquals("GD-202501151030-001", enviada.getCodigoGuia());
        assertEquals("Juan Perez", enviada.getTransportista());
        assertEquals("Santiago", enviada.getOrigen());
        assertEquals("Valparaíso", enviada.getDestino());
        assertEquals(150.0, enviada.getPesoKg());
    }

    @Test
    @DisplayName("enviarAColaPrincipal: usa exchange-guias y routing key guia.error")
    void enviarAColaPrincipal_debeUsarExchangeYRkCorrectos() {
        guiaProducer.enviarAColaPrincipal(guiaTest);

        ArgumentCaptor<String> routingKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                routingKeyCaptor.capture(),
                eq(guiaTest));

        assertEquals(RabbitMQConfig.ROUTING_KEY_ERROR, routingKeyCaptor.getValue(),
                "La routing key debe ser 'guia.error'");
    }

    @Test
    @DisplayName("enviarAColaPrincipal: envía el objeto incluso si el estado es CON_ERROR")
    void enviarAColaPrincipal_debeEnviarGuiaEnEstadoError() {
        guiaTest.setEstado(EstadoGuia.CON_ERROR);
        guiaProducer.enviarAColaPrincipal(guiaTest);

        verify(rabbitTemplate, times(1)).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_ERROR),
                eq(guiaTest));
    }

    @Test
    @DisplayName("No debería mezclar routing keys entre exitosa y error")
    void noDebeMezclarRoutingKeys() {
        guiaProducer.enviarAColaPrincipal(guiaTest);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_ERROR),
                any(Object.class));

        clearInvocations(rabbitTemplate);

        guiaProducer.enviarAColaPrincipal(guiaTest);
        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                eq(RabbitMQConfig.ROUTING_KEY_PRINCIPAL),
                any(Object.class));
    }
}
