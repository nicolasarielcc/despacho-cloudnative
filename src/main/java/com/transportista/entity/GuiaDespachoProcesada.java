package com.transportista.entity;

import com.transportista.enums.EstadoGuia;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad Guía de Despacho Procesada.
 *
 * Tabla NUEVA y DISTINTA a la usada en sumativas anteriores.
 * Aquí se guardan las guías que fueron consumidas exitosamente
 * desde la cola-guias-exitosas por el endpoint adicional
 * POST /api/cola/procesar-guias.
 *
 * Se almacena en la misma base de datos Oracle Cloud.
 */
@Entity
@Table(name = "GUIA_DESPACHO_PROCESADA")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GuiaDespachoProcesada {

    /**
     * ID autogenerado (secuencia independiente de GUIA_DESPACHO).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "guia_proc_seq")
    @SequenceGenerator(name = "guia_proc_seq", sequenceName = "SEQ_GUIA_PROCESADA", allocationSize = 1)
    private Long id;

    /**
     * Código de la guía original.
     */
    @Column(name = "CODIGO_GUIA", nullable = false, length = 50)
    private String codigoGuia;

    /**
     * Transportista responsable (heredado de la guía original).
     */
    @Column(name = "TRANSPORTISTA", length = 200)
    private String transportista;

    /**
     * Fecha de emisión de la guía original.
     */
    @Column(name = "FECHA_EMISION")
    private LocalDateTime fechaEmision;

    /**
     * Origen del despacho.
     */
    @Column(name = "ORIGEN", length = 300)
    private String origen;

    /**
     * Destino del despacho.
     */
    @Column(name = "DESTINO", length = 300)
    private String destino;

    /**
     * Descripción de la carga.
     */
    @Column(name = "DESCRIPCION_CARGA", length = 1000)
    private String descripcionCarga;

    /**
     * Peso en kg.
     */
    @Column(name = "PESO_KG")
    private Double pesoKg;

    /**
     * URL del archivo en S3.
     */
    @Column(name = "URL_S3", length = 500)
    private String urlS3;

    /**
     * Fecha en que la guía fue procesada desde la cola.
     * Este es el campo NUEVO que diferencia de la tabla GUIA_DESPACHO.
     */
    @Column(name = "FECHA_PROCESAMIENTO", nullable = false)
    private LocalDateTime fechaProcesamiento;

    /**
     * Estado al momento del procesamiento.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", length = 20)
    private EstadoGuia estado;

    @PrePersist
    protected void onCreate() {
        if (this.fechaProcesamiento == null) {
            this.fechaProcesamiento = LocalDateTime.now();
        }
    }
}
