package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Productor de mensajes RabbitMQ.
 *
 * Envía objetos GuiaDespacho a las colas configuradas.
 * Es llamado desde GuiaDespachoService cada vez que se crea o modifica una guía.
 *
 * Flujo:
 *   GuiaDespachoService.crearGuia()
 *     → guiaProducer.enviarGuiaExitosa(guia)      // éxito → cola 1
 *     → si falla: guiaProducer.enviarGuiaError(guia)  // error → cola 2
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuiaProducer {

    private final RabbitTemplate rabbitTemplate;

    /**
     * Envía una guía a la cola de guías exitosas (cola 1).
     *
     * La guía se serializa como JSON y se envía al exchange "exchange-guias"
     * con routing key "guia.exitosa".
     */
    public void enviarGuiaExitosa(GuiaDespacho guia) {
        log.info("Enviando guía {} a la cola de EXITOSAS", guia.getCodigoGuia());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_EXITOSA,
                guia);
        log.info("Guía {} enviada a cola-guias-exitosas correctamente", guia.getCodigoGuia());
    }

    /**
     * Envía una guía a la cola de errores (cola 2).
     *
     * Se usa cuando el procesamiento de la guía falla.
     * La guía se serializa como JSON y se envía al exchange "exchange-guias"
     * con routing key "guia.error".
     */
    public void enviarGuiaError(GuiaDespacho guia) {
        log.warn("Enviando guía {} a la cola de ERRORES", guia.getCodigoGuia());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.ROUTING_KEY_ERROR,
                guia);
        log.info("Guía {} enviada a cola-guias-error", guia.getCodigoGuia());
    }
}
