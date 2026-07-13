package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.enums.EstadoGuia;
import com.transportista.repository.GuiaDespachoProcesadaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * Consumidor de mensajes RabbitMQ.
 *
 * Escucha las colas y procesa los mensajes en tiempo real.
 * Se ejecuta automáticamente cuando la aplicación Spring Boot está corriendo.
 *
 * Flujo:
 *   cola-guias-exitosas → @RabbitListener → guardar en GuiaDespachoProcesada
 *   cola-guias-error    → @RabbitListener → log de error para depuración
 *
 * IMPORTANTE: Los mensajes se procesan como JSON (convertidos automáticamente
 * por Jackson2JsonMessageConverter configurado en RabbitMQConfig).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GuiaConsumer {

    private final GuiaDespachoProcesadaRepository procesadaRepository;

    /**
     * Consumidor de la cola de guías exitosas (cola 1).
     *
     * Cada mensaje recibido se guarda automáticamente en la tabla
     * GUIA_DESPACHO_PROCESADA (tabla NUEVA, distinta a las sumativas anteriores).
     *
     * @param guia el objeto GuiaDespacho deserializado desde JSON
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_EXITOSA)
    public void consumirGuiaExitosa(GuiaDespacho guia) {
        log.info("Mensaje recibido en cola-guias-exitosas: guía {}", guia.getCodigoGuia());

        try {
            // Crear registro en la tabla nueva (GUIA_DESPACHO_PROCESADA)
            GuiaDespachoProcesada procesada = GuiaDespachoProcesada.builder()
                    .codigoGuia(guia.getCodigoGuia())
                    .transportista(guia.getTransportista())
                    .fechaEmision(guia.getFechaEmision())
                    .origen(guia.getOrigen())
                    .destino(guia.getDestino())
                    .descripcionCarga(guia.getDescripcionCarga())
                    .pesoKg(guia.getPesoKg())
                    .urlS3(guia.getUrlS3())
                    .estado(EstadoGuia.ENVIADA)
                    .fechaProcesamiento(LocalDateTime.now())
                    .build();

            procesadaRepository.save(procesada);
            log.info("Guía {} procesada y guardada en GUIA_DESPACHO_PROCESADA", guia.getCodigoGuia());

        } catch (Exception e) {
            log.error("Error al procesar guía {}: {}", guia.getCodigoGuia(), e.getMessage(), e);
            // El mensaje NO se pierde: RabbitMQ lo reencola para reintento
            // Después de 3 reintentos fallidos, va al DLX (Dead Letter Exchange)
            throw new RuntimeException("Error procesando guía: " + guia.getCodigoGuia(), e);
        }
    }

    /**
     * Consumidor de la cola de errores (cola 2).
     *
     * Los mensajes en esta cola representan guías que fallaron en su creación
     * o modificación. Aquí se registran para depuración futura.
     *
     * @param guia el objeto GuiaDespacho con error
     */
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ERROR)
    public void consumirGuiaError(GuiaDespacho guia) {
        log.error("MENSAJE DE ERROR RECIBIDO en cola-guias-error");
        log.error("  Código guía: {}", guia.getCodigoGuia());
        log.error("  Transportista: {}", guia.getTransportista());
        log.error("  Origen: {}", guia.getOrigen());
        log.error("  Destino: {}", guia.getDestino());
        log.error("  Estado: {}", guia.getEstado());
        log.error("  Fecha emisión: {}", guia.getFechaEmision());
        log.error("  --- MENSAJE DE ERROR ALMACENADO PARA DEPURACIÓN ---");
        // Los mensajes en esta cola se almacenan hasta que un administrador
        // los revise manualmente desde la UI de RabbitMQ
    }
}
