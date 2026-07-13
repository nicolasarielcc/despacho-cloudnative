package com.transportista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sistema de Gestión de Pedidos y Generación de Guías de Despacho.
 *
 * Microservicio Cloud Native que integra:
 * - RabbitMQ para mensajería asíncrona (colas)
 * - AWS S3 para almacenamiento de archivos
 * - AWS API Gateway para exposición de endpoints
 * - Azure AD B2C para autenticación y autorización (IDaaS)
 * - Spring Security para securitización del backend
 * - Oracle Cloud para persistencia de datos
 */
@SpringBootApplication
public class TransportistaApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransportistaApplication.class, args);
    }
}
