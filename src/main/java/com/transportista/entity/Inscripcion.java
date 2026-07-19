package com.transportista.entity;

import com.transportista.enums.EstadoInscripcion;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad: Inscripción.
 *
 * Representa la inscripción de un estudiante a un curso online.
 * Se persiste en la base de datos Oracle Cloud.
 * Los atributos id, fechaInscripcion y estado se generan automáticamente.
 */
@Entity
@Table(name = "INSCRIPCION")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inscripcion {

    /**
     * ID autogenerado por la base de datos (secuencia Oracle).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "inscripcion_seq")
    @SequenceGenerator(name = "inscripcion_seq", sequenceName = "SEQ_INSCRIPCION", allocationSize = 1)
    private Long id;

    /**
     * Código del curso al que se inscribe el estudiante.
     */
    @Column(name = "CODIGO_CURSO", nullable = false, length = 50)
    private String codigoCurso;

    /**
     * Nombre completo del estudiante.
     */
    @Column(name = "ESTUDIANTE", nullable = false, length = 200)
    private String estudiante;

    /**
     * Correo electrónico del estudiante.
     */
    @Column(name = "EMAIL_ESTUDIANTE", nullable = false, length = 200)
    private String emailEstudiante;

    /**
     * Fecha y hora en que se realizó la inscripción.
     */
    @Column(name = "FECHA_INSCRIPCION")
    private LocalDateTime fechaInscripcion;

    /**
     * Calificación obtenida por el estudiante (0.0 a 10.0).
     */
    @Column(name = "CALIFICACION")
    private Double calificacion;

    /**
     * Estado actual de la inscripción: PENDIENTE, INSCRITO, CALIFICADO, CON_ERROR.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoInscripcion estado;

    /**
     * Fecha y hora de procesamiento. Se asigna automáticamente.
     */
    @Column(name = "FECHA_PROCESAMIENTO")
    private LocalDateTime fechaProcesamiento;

    /**
     * Asigna la fecha de procesamiento y estado por defecto antes de persistir.
     */
    @PrePersist
    protected void onCreate() {
        if (this.fechaProcesamiento == null) {
            this.fechaProcesamiento = LocalDateTime.now();
        }
        if (this.estado == null) {
            this.estado = EstadoInscripcion.PENDIENTE;
        }
    }
}
