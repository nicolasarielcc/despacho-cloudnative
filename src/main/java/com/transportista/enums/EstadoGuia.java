package com.transportista.enums;

/**
 * Estados posibles de una guía de despacho.
 * - PENDIENTE:  recién creada, aún no enviada a la cola
 * - ENVIADA:    procesada exitosamente, enviada a cola-guias-exitosas
 * - CON_ERROR:  falló el procesamiento, enviada a cola-guias-error
 */
public enum EstadoGuia {

    PENDIENTE,
    ENVIADA,
    CON_ERROR
}
