package com.duoc.despacho_service.dto.request;

import com.duoc.despacho_service.enums.EstadoGuia;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class GuiaRequestDTO {

    @NotBlank(message = "El número de guía es obligatorio")
    private String numeroGuia;

    @NotBlank(message = "El transportista es obligatorio")
    private String transportista;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDate fecha;

    @NotBlank(message = "La dirección de origen es obligatoria")
    private String direccionOrigen;

    @NotBlank(message = "La dirección de destino es obligatoria")
    private String direccionDestino;

    private String descripcionCarga;

    @NotNull(message = "El estado es obligatorio")
    private EstadoGuia estado;
}