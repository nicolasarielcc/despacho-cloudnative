package com.transportista.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursoResponse {

    private Long id;
    private String codigoCurso;
    private String nombre;
    private String instructor;
    private String descripcion;
    private Double creditos;
    private LocalDateTime fechaInicio;
    private LocalDateTime fechaFin;
    private String estado;
    private String urlS3;
    private LocalDateTime fechaCreacion;
}
