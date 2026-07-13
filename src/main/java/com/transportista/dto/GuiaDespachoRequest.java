package com.transportista.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO para crear o actualizar una guía de despacho.
 * Contiene validaciones con Jakarta Bean Validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespachoRequest {

    /**
     * Nombre o ID del transportista asignado a la guía.
     */
    @NotBlank(message = "El transportista es obligatorio")
    @Size(max = 200)
    private String transportista;

    /**
     * Fecha y hora de emisión. Si es null, se usa la fecha actual.
     */
    @NotNull(message = "La fecha de emisión es obligatoria")
    private LocalDateTime fechaEmision;

    /**
     * Dirección de origen del despacho.
     */
    @NotBlank(message = "El origen es obligatorio")
    @Size(max = 300)
    private String origen;

    /**
     * Dirección de destino del despacho.
     */
    @NotBlank(message = "El destino es obligatorio")
    @Size(max = 300)
    private String destino;

    /**
     * Descripción de la carga transportada (opcional).
     */
    @Size(max = 1000)
    private String descripcionCarga;

    /**
     * Peso en kilogramos de la carga (opcional, mínimo 0).
     */
    @DecimalMin(value = "0.0", message = "El peso debe ser >= 0")
    private Double pesoKg;
}
