package com.transportista.dto;

import com.transportista.enums.EstadoGuia;
import lombok.*;

import java.time.LocalDateTime;

/**
 * DTO de respuesta para devolver datos de una guía de despacho al frontend.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespachoResponse {

    private Long id;
    private String codigoGuia;
    private String transportista;
    private LocalDateTime fechaEmision;
    private String origen;
    private String destino;
    private String descripcionCarga;
    private Double pesoKg;
    private EstadoGuia estado;
    private String urlS3;
    private LocalDateTime fechaCreacion;
}
