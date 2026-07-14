package com.transportista.rabbitmq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String QUEUE_PRINCIPAL = "cola-guias-principal";

    public static final String QUEUE_ERROR_DLQ = "cola-guias-dlq";

    public static final String EXCHANGE = "exchange-guias";

    public static final String ROUTING_KEY_PRINCIPAL = "guia.nueva";

    public static final String ROUTING_KEY_ERROR = "guia.error";

    public static final String DLX_EXCHANGE = "dlx-exchange";

    public static final String DLX_ROUTING_KEY = "dlx-routing-key";

    @Bean
    public Queue colaPrincipal() {
        return QueueBuilder.durable(QUEUE_PRINCIPAL)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", DLX_ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue colaDlq() {
        return new Queue(QUEUE_ERROR_DLQ, true);
    }

    @Bean
    public DirectExchange exchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE);
    }

    @Bean
    public Binding bindingPrincipal() {
        return BindingBuilder
                .bind(colaPrincipal())
                .to(exchange())
                .with(ROUTING_KEY_PRINCIPAL);
    }

    @Bean
    public Binding bindingDlq() {
        return BindingBuilder
                .bind(colaDlq())
                .to(dlxExchange())
                .with(DLX_ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter());
        return template;
    }
}
