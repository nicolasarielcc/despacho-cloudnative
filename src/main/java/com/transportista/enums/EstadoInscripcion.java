package com.transportista.enums;

/**
 * Estados posibles de una inscripción a un curso.
 * - PENDIENTE:  inscripción creada, aún no procesada
 * - INSCRITO:   estudiante inscrito exitosamente
 * - CALIFICADO: estudiante calificado en el curso
 * - CON_ERROR:  falló el procesamiento de la inscripción
 */
public enum EstadoInscripcion {

    PENDIENTE,
    INSCRITO,
    CALIFICADO,
    CON_ERROR
}
