package com.transportista.rabbitmq;

import com.transportista.entity.Curso;
import com.transportista.enums.EstadoCurso;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.time.LocalDateTime;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("CursoProducer - Unit Tests")
class CursoProducerTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CursoProducer cursoProducer;

    private Curso crearCurso() {
        return Curso.builder()
                .id(1L)
                .codigoCurso("CUR-202501151030-001")
                .nombre("Curso de Java")
                .instructor("Juan Perez")
                .descripcion("Curso avanzado de Java")
                .creditos(5.0)
                .fechaInicio(LocalDateTime.of(2025, 1, 15, 10, 30))
                .estado(EstadoCurso.PENDIENTE)
                .fechaCreacion(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("enviarAColaPrincipal: envia a EXCHANGE con ROUTING_KEY_PRINCIPAL")
    void enviarAColaPrincipal() {
        Curso curso = crearCurso();

        cursoProducer.enviarAColaPrincipal(curso);

        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_PRINCIPAL,
                curso);
    }

    @Test
    @DisplayName("enviarAColaDlq: envia a DLX_EXCHANGE con DLX_ROUTING_KEY")
    void enviarAColaDlq() {
        Curso curso = crearCurso();

        cursoProducer.enviarAColaDlq(curso);

        verify(rabbitTemplate, times(1)).convertAndSend(
                RabbitMQConfig.DLX_EXCHANGE,
                RabbitMQConfig.DLX_ROUTING_KEY,
                curso);
    }

    @Test
    @DisplayName("noDebeMezclarRoutingKeys: enviarAColaPrincipal no usa DLX exchange")
    void noDebeMezclarRoutingKeys_principal() {
        Curso curso = crearCurso();

        cursoProducer.enviarAColaPrincipal(curso);

        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.DLX_EXCHANGE),
                anyString(),
                any(Curso.class));
    }

    @Test
    @DisplayName("noDebeMezclarRoutingKeys: enviarAColaDlq no usa principal exchange")
    void noDebeMezclarRoutingKeys_dlq() {
        Curso curso = crearCurso();

        cursoProducer.enviarAColaDlq(curso);

        verify(rabbitTemplate, never()).convertAndSend(
                eq(RabbitMQConfig.EXCHANGE),
                anyString(),
                any(Curso.class));
    }
}
