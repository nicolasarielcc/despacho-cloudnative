package com.duoc.despacho_service.enums;

/**
 * Enum que representa los posibles estados de una guía de despacho.
 * - PENDIENTE: La guía de despacho ha sido creada pero aún no se ha enviado.
 * - ENVIADO: La guía de despacho ha sido enviada pero aún no se ha entregado.
 * - ENTREGADO: La guía de despacho ha sido entregada al destinatario.
 */
public enum EstadoGuia {

    PENDIENTE,
    ENVIADO,
    ENTREGADO,

}
