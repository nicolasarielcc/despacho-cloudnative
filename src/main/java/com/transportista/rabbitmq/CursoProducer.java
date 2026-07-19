package com.transportista.rabbitmq;

import com.transportista.entity.Curso;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class CursoProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enviarAColaPrincipal(Curso curso) {
        log.info("PRODUCER: Enviando curso {} a cola principal", curso.getCodigoCurso());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_PRINCIPAL,
                curso);
        log.info("PRODUCER: Curso {} enviado a {}", curso.getCodigoCurso(), RabbitMQConfig.QUEUE_PRINCIPAL);
    }

    public void enviarAColaDlq(Curso curso) {
        log.info("PRODUCER: Enviando curso {} a DLQ (cola de errores)", curso.getCodigoCurso());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DLX_EXCHANGE,
                RabbitMQConfig.DLX_ROUTING_KEY,
                curso);
        log.info("PRODUCER: Curso {} enviado a {}", curso.getCodigoCurso(), RabbitMQConfig.QUEUE_ERROR_DLQ);
    }

    public Curso consumirDeColaPrincipal() {
        log.info("CONSUMER HTTP: Intentando consumir mensaje de {}", RabbitMQConfig.QUEUE_PRINCIPAL);
        Object mensaje = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_PRINCIPAL);
        if (mensaje instanceof Curso curso) {
            log.info("CONSUMER HTTP: Mensaje consumido - curso {}", curso.getCodigoCurso());
            return curso;
        }
        log.info("CONSUMER HTTP: No hay mensajes pendientes en la cola principal");
        return null;
    }
}
