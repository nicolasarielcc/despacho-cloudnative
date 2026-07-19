package com.transportista.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscripcionResponse {

    private Long id;
    private String codigoCurso;
    private String estudiante;
    private String emailEstudiante;
    private LocalDateTime fechaInscripcion;
    private Double calificacion;
    private String estado;
    private LocalDateTime fechaProcesamiento;
}
