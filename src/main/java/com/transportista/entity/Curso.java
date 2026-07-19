package com.transportista.entity;

import com.transportista.enums.EstadoCurso;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Entidad: Curso.
 *
 * Representa un curso online disponible para inscripción de estudiantes.
 * Se persiste en la base de datos Oracle Cloud.
 * Los atributos id, fechaCreacion y estado se generan automáticamente.
 */
@Entity
@Table(name = "CURSO")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Curso {

    /**
     * ID autogenerado por la base de datos (secuencia Oracle).
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "curso_seq")
    @SequenceGenerator(name = "curso_seq", sequenceName = "SEQ_CURSO", allocationSize = 1)
    private Long id;

    /**
     * Código único del curso (ej: CURSO-2025-001).
     */
    @Column(name = "CODIGO_CURSO", unique = true, nullable = false, length = 50)
    private String codigoCurso;

    /**
     * Nombre del curso.
     */
    @Column(name = "NOMBRE", nullable = false, length = 200)
    private String nombre;

    /**
     * Instructor a cargo del curso.
     */
    @Column(name = "INSTRUCTOR", nullable = false, length = 200)
    private String instructor;

    /**
     * Descripción detallada del contenido del curso.
     */
    @Column(name = "DESCRIPCION", length = 1000)
    private String descripcion;

    /**
     * Cantidad de créditos que otorga el curso.
     */
    @Column(name = "CREDITOS")
    private Double creditos;

    /**
     * Fecha y hora de inicio del curso.
     */
    @Column(name = "FECHA_INICIO")
    private LocalDateTime fechaInicio;

    /**
     * Fecha y hora de finalización del curso.
     */
    @Column(name = "FECHA_FIN")
    private LocalDateTime fechaFin;

    /**
     * Estado actual del curso: PENDIENTE, PUBLICADO, CERRADO.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "ESTADO", nullable = false, length = 20)
    private EstadoCurso estado;

    /**
     * URL del archivo del curso en S3.
     */
    @Column(name = "URL_S3", length = 500)
    private String urlS3;

    /**
     * Fecha y hora de creación del registro. Se asigna automáticamente.
     */
    @Column(name = "FECHA_CREACION", nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Asigna la fecha de creación y estado por defecto antes de persistir.
     */
    @PrePersist
    protected void onCreate() {
        if (this.fechaCreacion == null) {
            this.fechaCreacion = LocalDateTime.now();
        }
        if (this.estado == null) {
            this.estado = EstadoCurso.PENDIENTE;
        }
    }
}
