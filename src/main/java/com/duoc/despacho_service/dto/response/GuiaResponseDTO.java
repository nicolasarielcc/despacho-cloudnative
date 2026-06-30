package com.duoc.despacho_service.dto.response;

import com.duoc.despacho_service.enums.EstadoGuia;
import lombok.Data;
import java.time.LocalDate;

@Data
public class GuiaResponseDTO {

    private Long id;
    private String numeroGuia;
    private String transportista;
    private LocalDate fecha;
    private String direccionOrigen;
    private String direccionDestino;
    private String descripcionCarga;
    private EstadoGuia estado;
    private String rutaEfs;
    private String rutaS3;
    
}