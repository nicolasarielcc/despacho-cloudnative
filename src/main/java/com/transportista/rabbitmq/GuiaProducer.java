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
}
