package com.transportista.rabbitmq;

import com.transportista.entity.GuiaDespacho;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.enums.EstadoGuia;
import com.transportista.repository.GuiaDespachoProcesadaRepository;
import com.transportista.repository.GuiaDespachoRepository;
import com.transportista.service.S3Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Slf4j
@Component
@RequiredArgsConstructor
public class GuiaConsumer {

    private final GuiaDespachoProcesadaRepository procesadaRepository;
    private final GuiaDespachoRepository guiaRepository;
    private final S3Service s3Service;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PRINCIPAL)
    @Transactional
    public void procesarGuia(GuiaDespacho guia) {
        log.info("CONSUMER: Mensaje recibido - guia {}", guia.getCodigoGuia());

        String contenidoPdf = generarContenidoGuia(guia);
        byte[] pdfBytes = contenidoPdf.getBytes();

        // SIMULACION DE ERROR: Si el transportista es "ERROR", fuerza fallo para probar DLQ
        if ("ERROR".equalsIgnoreCase(guia.getTransportista())) {
            log.error("CONSUMER: ERROR SIMULADO - Enviando guia {} a DLQ", guia.getCodigoGuia());
            throw new RuntimeException("ERROR SIMULADO: Transportista invalido - demostracion DLQ");
        }

        try {
            String urlS3 = s3Service.subirGuia(
                    guia.getTransportista(),
                    guia.getCodigoGuia(),
                    pdfBytes);
            log.info("CONSUMER: PDF subido a S3 - {}", urlS3);

            GuiaDespachoProcesada procesada = GuiaDespachoProcesada.builder()
                    .codigoGuia(guia.getCodigoGuia())
                    .transportista(guia.getTransportista())
                    .fechaEmision(guia.getFechaEmision())
                    .origen(guia.getOrigen())
                    .destino(guia.getDestino())
                    .descripcionCarga(guia.getDescripcionCarga())
                    .pesoKg(guia.getPesoKg())
                    .urlS3(urlS3)
                    .estado(EstadoGuia.ENVIADA)
                    .fechaProcesamiento(LocalDateTime.now())
                    .build();

            procesadaRepository.save(procesada);
            log.info("CONSUMER: Guia guardada en BD tabla GUIA_DESPACHO_PROCESADA");

            guia.setEstado(EstadoGuia.ENVIADA);
            guia.setUrlS3(urlS3);
            guiaRepository.save(guia);

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("CONSUMER: Error procesando guia {}: {}", guia.getCodigoGuia(), e.getMessage());
            throw new RuntimeException("Error en consumidor - reenviando a DLQ", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ERROR_DLQ)
    public void procesarDlq(GuiaDespacho guia) {
        log.error("DLQ: Mensaje recibido en Dead Letter Queue");
        log.error("DLQ: Guia fallida - Codigo: {}, Transportista: {}, Origen: {}, Destino: {}",
                guia.getCodigoGuia(), guia.getTransportista(),
                guia.getOrigen(), guia.getDestino());

        guia.setEstado(EstadoGuia.CON_ERROR);
        guiaRepository.save(guia);

        log.error("DLQ: Guia {} marcada como CON_ERROR para revision manual", guia.getCodigoGuia());
    }

    private String generarContenidoGuia(GuiaDespacho guia) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("       GUIA DE DESPACHO OFICIAL\n");
        sb.append("===========================================\n\n");
        sb.append("Codigo Guia:     ").append(guia.getCodigoGuia()).append("\n");
        sb.append("Transportista:   ").append(guia.getTransportista()).append("\n");
        sb.append("Fecha Emision:   ").append(
                guia.getFechaEmision() != null
                        ? guia.getFechaEmision().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        : "N/A").append("\n");
        sb.append("Origen:          ").append(guia.getOrigen()).append("\n");
        sb.append("Destino:         ").append(guia.getDestino()).append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("Carga:\n");
        sb.append("  Descripcion:   ").append(guia.getDescripcionCarga()).append("\n");
        sb.append("  Peso (kg):     ").append(guia.getPesoKg()).append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("Estado:          ").append(guia.getEstado()).append("\n");
        sb.append("URL S3:          ").append(
                guia.getUrlS3() != null ? guia.getUrlS3() : "Pendiente").append("\n\n");
        sb.append("===========================================\n");
        sb.append("Documento generado automaticamente\n");
        sb.append("Sistema de Gestion de Guias de Despacho\n");
        sb.append("Fecha: ").append(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        sb.append("===========================================\n");
        return sb.toString();
    }
}
