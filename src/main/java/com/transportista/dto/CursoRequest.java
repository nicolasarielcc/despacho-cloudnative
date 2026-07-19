package com.transportista.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CursoRequest {

    @NotBlank
    @Size(max = 200)
    private String nombre;

    @NotBlank
    @Size(max = 200)
    private String instructor;

    @Size(max = 1000)
    private String descripcion;

    @DecimalMin("0.0")
    private Double creditos;

    @NotNull
    private LocalDateTime fechaInicio;

    private LocalDateTime fechaFin;
}
