package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuiaProducer {

    private final RabbitTemplate rabbitTemplate;

    public void enviarAColaPrincipal(GuiaDespacho guia) {
        log.info("PRODUCER: Enviando guia {} a cola principal", guia.getCodigoGuia());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_PRINCIPAL,
                guia);
        log.info("PRODUCER: Guia {} enviada a {}", guia.getCodigoGuia(), RabbitMQConfig.QUEUE_PRINCIPAL);
    }

    public void enviarAColaDlq(GuiaDespacho guia) {
        log.info("PRODUCER: Enviando guia {} a DLQ (cola de errores)", guia.getCodigoGuia());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.DLX_EXCHANGE,
                RabbitMQConfig.DLX_ROUTING_KEY,
                guia);
        log.info("PRODUCER: Guia {} enviada a {}", guia.getCodigoGuia(), RabbitMQConfig.QUEUE_ERROR_DLQ);
    }

    public GuiaDespacho consumirDeColaPrincipal() {
        log.info("CONSUMER HTTP: Intentando consumir mensaje de {}", RabbitMQConfig.QUEUE_PRINCIPAL);
        Object mensaje = rabbitTemplate.receiveAndConvert(RabbitMQConfig.QUEUE_PRINCIPAL);
        if (mensaje instanceof GuiaDespacho guia) {
            log.info("CONSUMER HTTP: Mensaje consumido - guia {}", guia.getCodigoGuia());
            return guia;
        }
        log.info("CONSUMER HTTP: No hay mensajes pendientes en la cola principal");
        return null;
    }
}
