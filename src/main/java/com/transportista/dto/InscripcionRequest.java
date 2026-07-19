package com.transportista.dto;

import jakarta.validation.constraints.*;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InscripcionRequest {

    @NotBlank
    @Size(max = 50)
    private String codigoCurso;

    @NotBlank
    @Size(max = 200)
    private String estudiante;

    @Email
    @Size(max = 200)
    private String emailEstudiante;
}
