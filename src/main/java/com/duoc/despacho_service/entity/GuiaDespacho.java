package com.duoc.despacho_service.entity;

import com.duoc.despacho_service.enums.EstadoGuia;
import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDate;

@Data
@Entity
@Table(name = "guias_despacho")
public class GuiaDespacho {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String numeroGuia;

    @Column(nullable = false)
    private String transportista;

    @Column(nullable = false)
    private LocalDate fecha;

    @Column(nullable = false)
    private String direccionOrigen;

    @Column(nullable = false)
    private String direccionDestino;

    private String descripcionCarga;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoGuia estado;

    // Rutas de almacenamiento
    private String rutaEfs;   // path local en EFS
    private String rutaS3;    // key del objeto en S3
    
}