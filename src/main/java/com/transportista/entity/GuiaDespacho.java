package com.transportista.entity;

import com.transportista.enums.EstadoGuia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad principal: Guía de Despacho.
 *
 * Representa una guía de despacho generada para un transportista.
 * Se persiste en la base de datos Oracle Cloud.
 * Los atributos id, fechaCreacion se generan automáticamente.
 */
@Entity
@Table(name = "GUIA_DESPACHO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespacho {

    /**
     * ID autogenerado por la base de datos (secuencia Oracle).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "guia_seq")
    @SequenceGenerator(name = "guia_seq", sequenceName = "SEQ_GUIA_DESPACHO", allocationSize = 1)
    private Long id;

    /**
     * Código único de la guía (ej: GD-2025-001).
     * Se genera automáticamente en el service.
     */
    @Column(name = "CODIGO_GUIA", unique = true, nullable = false, length = 50)
    private String codigoGuia;

    /**
     * Nombre o identificador del transportista responsable.
     */
    @Column(name = "TRANSPORTISTA", nullable = false, length = 200)
    private String transportista;

    /**
     * Fecha y hora de emisión de la guía.
     */
    @Column(name = "FECHA_EMISION", nullable = false)
    private LocalDateTime fechaEmision;

    /**
     * Dirección o ciudad de origen del despacho.
     */
    @Column(name = "ORIGEN", nullable = false, length = 300)
    private String origen;

    /**
     * Dirección o ciudad de destino del despacho.
     */
    @Column(name = "DESTINO", nullable = false, length = 300)
    private String destino;

    /**
     * Descripción detallada de la carga transportada.
     */
    @Column(name = "DESCRIPCION_CARGA", length = 1000)
    private String descripcionCarga;

    /**
     * Peso de la carga en kilogramos.
     */
    @Column(name = "PESO_KG")
    private Double pesoKg;

    /**
     * Estado actual de la guía: PENDIENTE, ENVIADA, CON_ERROR.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoGuia estado;

    /**
     * URL del archivo en S3 (se completa cuando se sube la guía).
     */
    @Column(name = "URL_S3", length = 500)
    private String urlS3;

    /**
     * Fecha y hora de creación del registro. Se asigna automáticamente.
     */
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Asigna la fecha de creación antes de persistir por primera vez.
     */
    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.estado == null) {
            this.estado = EstadoGuia.PENDIENTE;
        }
    }
}
