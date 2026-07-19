package com.transportista;

import com.transportista.rabbitmq.RabbitMQConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Prueba de contexto de Spring Boot (Criterio 2 + 3).
 *
 * Verifica que:
 * 1. El contexto de Spring Boot carga correctamente (todas las dependencias resueltas)
 * 2. Los beans de RabbitMQ están configurados (colas, exchange, bindings)
 * 3. Los beans de seguridad están configurados (SecurityFilterChain)
 * 4. El perfil test carga H2 en memoria (no requiere Oracle)
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("Contexto de la aplicación")
class TransportistaApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Test
    @DisplayName("El contexto de Spring Boot carga correctamente")
    void contextLoads() {
        assertNotNull(context, "El ApplicationContext no debería ser null");
        assertTrue(context.getBeanDefinitionCount() > 0, "Debe haber beans definidos");
    }

    // ================================================================
    // CRITERIO 2 — RabbitMQ (beans de configuración)
    // ================================================================

    @Test
    @DisplayName("Criterio 2: Bean colaPrincipal está configurado")
    void rabbitMQ_colaPrincipal_estaConfigurada() {
        Queue cola = context.getBean("colaPrincipal", Queue.class);
        assertNotNull(cola, "La cola principal debe existir");
        assertEquals(RabbitMQConfig.QUEUE_PRINCIPAL, cola.getName(), "Nombre correcto de cola");
        assertTrue(cola.isDurable(), "La cola debe ser durable");
    }

    @Test
    @DisplayName("Criterio 2: Bean colaDlq está configurado")
    void rabbitMQ_colaDlq_estaConfigurada() {
        Queue cola = context.getBean("colaDlq", Queue.class);
        assertNotNull(cola, "La cola dlq debe existir");
        assertEquals(RabbitMQConfig.QUEUE_ERROR_DLQ, cola.getName(), "Nombre correcto de cola");
    }

    @Test
    @DisplayName("Criterio 2: Bean exchange está configurado (tipo direct)")
    void rabbitMQ_exchange_estaConfigurado() {
        DirectExchange exchange = context.getBean("exchange", DirectExchange.class);
        assertNotNull(exchange, "El exchange debe existir");
        assertEquals(RabbitMQConfig.EXCHANGE, exchange.getName(), "Nombre correcto de exchange");
    }

    @Test
    @DisplayName("Criterio 2: Binding principal existe (cola-cursos-principal ← exchange-cursos)")
    void rabbitMQ_bindingPrincipal_existe() {
        Binding binding = context.getBean("bindingPrincipal", Binding.class);
        assertNotNull(binding, "Binding principal debe existir");
        assertEquals(RabbitMQConfig.QUEUE_PRINCIPAL, binding.getDestination(), "Destino correcto");
        assertEquals(RabbitMQConfig.EXCHANGE, binding.getExchange(), "Exchange correcto");
        assertEquals(RabbitMQConfig.ROUTING_KEY_PRINCIPAL, binding.getRoutingKey(), "Routing key correcta");
    }

    @Test
    @DisplayName("Criterio 2: Binding dlq existe (cola-cursos-dlq ← dlx-exchange)")
    void rabbitMQ_bindingDlq_existe() {
        Binding binding = context.getBean("bindingDlq", Binding.class);
        assertNotNull(binding, "Binding dlq debe existir");
        assertEquals(RabbitMQConfig.QUEUE_ERROR_DLQ, binding.getDestination(), "Destino correcto");
        assertEquals(RabbitMQConfig.DLX_EXCHANGE, binding.getExchange(), "Exchange DLX correcto");
        assertEquals(RabbitMQConfig.DLX_ROUTING_KEY, binding.getRoutingKey(), "Routing key correcta");
    }

    @Test
    @DisplayName("Criterio 2: Las routing keys son diferentes entre sí")
    void rabbitMQ_routingKeys_diferentes() {
        assertNotEquals(RabbitMQConfig.ROUTING_KEY_PRINCIPAL, RabbitMQConfig.ROUTING_KEY_ERROR,
                "Las routing keys deben ser diferentes");
        assertNotEquals(RabbitMQConfig.QUEUE_PRINCIPAL, RabbitMQConfig.QUEUE_ERROR_DLQ,
                "Los nombres de cola deben ser diferentes");
    }

    // ================================================================
    // CRITERIO 3 — Spring Security (beans de seguridad)
    // ================================================================

    @Test
    @DisplayName("Criterio 3: SecurityFilterChain está configurado")
    void security_filterChain_estaConfigurado() {
        Object chain = context.getBean("securityFilterChain");
        assertNotNull(chain, "El SecurityFilterChain debe existir");
    }

    @Test
    @DisplayName("Criterio 3: JwtAuthenticationConverter está configurado")
    void security_jwtConverter_estaConfigurado() {
        Object converter = context.getBean("jwtAuthenticationConverter");
        assertNotNull(converter, "El JwtAuthenticationConverter debe existir");
    }

    @Test
    @DisplayName("Criterio 3: CorsConfigurationSource está configurado")
    void security_corsConfig_estaConfigurado() {
        Object cors = context.getBean("corsConfigurationSource");
        assertNotNull(cors, "CorsConfigurationSource debe existir");
    }

    @Test
    @DisplayName("Perfil test: base de datos H2 está configurada")
    void usarBaseDatosH2() {
        // Verificar que el perfil test usa H2 (no Oracle)
        String datasourceUrl = context.getEnvironment()
                .getProperty("spring.datasource.url", "");
        assertTrue(datasourceUrl.contains("h2"),
                "En perfil test, la URL debe ser H2. Actual: " + datasourceUrl);
    }
}
