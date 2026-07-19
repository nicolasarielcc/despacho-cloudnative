package com.transportista;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Sistema de Gestión de Cursos en Línea.
 *
 * Plataforma Cloud Native que integra:
 * - RabbitMQ para mensajería asíncrona (colas de inscripciones)
 * - AWS S3 para almacenamiento de material de cursos
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
