package com.transportista.rabbitmq;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de RabbitMQ: colas, exchanges, bindings y conversores.
 *
 * Estructura:
 *   Exchange (exchange-guias, tipo direct)
 *     ├── Cola 1 (cola-guias-exitosas)  → routing key: guia.exitosa
 *     └── Cola 2 (cola-guias-error)     → routing key: guia.error
 *
 * IMPORTANTE:
 * - Las colas se crean como durables (sobreviven reinicios de RabbitMQ).
 * - Los mensajes se serializan como JSON usando Jackson2JsonMessageConverter.
 * - Para crear estas colas manualmente en la UI de RabbitMQ (http://localhost:15672):
 *   1. Ve a Queues → Add a new queue → nombre: cola-guias-exitosas
 *   2. Ve a Exchanges → Add a new exchange → nombre: exchange-guias, tipo: direct
 *   3. En Bindings, asocia las colas con sus routing keys
 */
@Configuration
public class RabbitMQConfig {

    // ============================================================
    // NOMBRES DE COLAS, EXCHANGE Y ROUTING KEYS
    // Estos mismos nombres deben coincidir con los creados en la UI
    // de administración de RabbitMQ (http://localhost:15672)
    // ============================================================

    /** Cola para guías procesadas exitosamente */
    public static final String QUEUE_EXITOSA = "cola-guias-exitosas";

    /** Cola para guías con error */
    public static final String QUEUE_ERROR = "cola-guias-error";

    /** Exchange principal (tipo direct) */
    public static final String EXCHANGE = "exchange-guias";

    /** Routing key para enrutar mensajes a la cola de exitosas */
    public static final String ROUTING_KEY_EXITOSA = "guia.exitosa";

    /** Routing key para enrutar mensajes a la cola de errores */
    public static final String ROUTING_KEY_ERROR = "guia.error";

    // --- Definición de Colas ---

    /**
     * Cola 1: guías exitosas.
     * Durability: true → sobrevive reinicios de RabbitMQ.
     */
    @Bean
    public Queue colaExitosa() {
        return new Queue(QUEUE_EXITOSA, true);
    }

    /**
     * Cola 2: guías con error.
     * Configuración con Dead Letter Exchange (DLX) para mensajes fallidos.
     * Si un mensaje es rechazado 3 veces, va a la DLX.
     */
    @Bean
    public Queue colaError() {
        return QueueBuilder.durable(QUEUE_ERROR)
                .withArgument("x-dead-letter-exchange", "dlx-exchange")
                .withArgument("x-dead-letter-routing-key", "dlx-routing-key")
                .build();
    }

    // --- Exchange ---

    /**
     * Exchange tipo Direct: enruta mensajes según la routing key exacta.
     */
    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    // --- Bindings (asociaciones cola-exchange) ---

    /**
     * Binding: cola de exitosas ← exchange, routing key "guia.exitosa"
     */
    @Bean
    public Binding bindingExitosa() {
        return BindingBuilder
                .bind(colaExitosa())
                .to(exchange())
                .with(ROUTING_KEY_EXITOSA);
    }

    /**
     * Binding: cola de errores ← exchange, routing key "guia.error"
     */
    @Bean
    public Binding bindingError() {
        return BindingBuilder
                .bind(colaError())
                .to(exchange())
                .with(ROUTING_KEY_ERROR);
    }

    // --- Conversor de mensajes ---

    /**
     * Convierte objetos Java a JSON y viceversa al enviar/recibir mensajes.
     * Sin esto, RabbitMQ usaría serialización binaria de Java (no portable).
     */
    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * Configura RabbitTemplate con el conversor JSON.
     * RabbitTemplate es la clase principal para enviar mensajes a RabbitMQ.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
