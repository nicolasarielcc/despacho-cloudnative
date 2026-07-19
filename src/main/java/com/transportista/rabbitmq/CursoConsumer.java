package com.transportista.rabbitmq;

import com.transportista.entity.Inscripcion;
import com.transportista.enums.EstadoInscripcion;
import com.transportista.repository.CursoRepository;
import com.transportista.repository.InscripcionRepository;
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
public class CursoConsumer {

    private final InscripcionRepository inscripcionRepository;
    private final CursoRepository cursoRepository;
    private final S3Service s3Service;

    @RabbitListener(queues = RabbitMQConfig.QUEUE_PRINCIPAL)
    @Transactional
    public void procesarInscripcion(Inscripcion inscripcion) {
        log.info("CONSUMER: Mensaje recibido - inscripcion de {} al curso {}",
                inscripcion.getEstudiante(), inscripcion.getCodigoCurso());

        String contenidoPdf = generarContenidoInscripcion(inscripcion);
        byte[] pdfBytes = contenidoPdf.getBytes();

        if ("ERROR".equalsIgnoreCase(inscripcion.getEstudiante())) {
            log.error("CONSUMER: ERROR SIMULADO - Enviando inscripcion de {} a DLQ", inscripcion.getEstudiante());
            throw new RuntimeException("ERROR SIMULADO: Estudiante invalido - demostracion DLQ");
        }

        try {
            String urlS3 = s3Service.subirGuia(
                    inscripcion.getEstudiante(),
                    inscripcion.getCodigoCurso(),
                    pdfBytes);
            log.info("CONSUMER: PDF subido a S3 - {}", urlS3);

            inscripcion.setEstado(EstadoInscripcion.INSCRITO);
            inscripcion.setFechaProcesamiento(LocalDateTime.now());
            inscripcionRepository.save(inscripcion);
            log.info("CONSUMER: Inscripcion guardada en BD tabla INSCRIPCION");

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("CONSUMER: Error procesando inscripcion de {}: {}",
                    inscripcion.getEstudiante(), e.getMessage());
            throw new RuntimeException("Error en consumidor - reenviando a DLQ", e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE_ERROR_DLQ)
    public void procesarDlq(Inscripcion inscripcion) {
        log.error("DLQ: Mensaje recibido en Dead Letter Queue");
        log.error("DLQ: Inscripcion fallida - Codigo Curso: {}, Estudiante: {}, Email: {}",
                inscripcion.getCodigoCurso(), inscripcion.getEstudiante(),
                inscripcion.getEmailEstudiante());

        inscripcion.setEstado(EstadoInscripcion.CON_ERROR);
        inscripcionRepository.save(inscripcion);

        log.error("DLQ: Inscripcion de {} marcada como CON_ERROR para revision manual",
                inscripcion.getEstudiante());
    }

    private String generarContenidoInscripcion(Inscripcion inscripcion) {
        StringBuilder sb = new StringBuilder();
        sb.append("===========================================\n");
        sb.append("       COMPROBANTE DE INSCRIPCION\n");
        sb.append("===========================================\n\n");
        sb.append("Codigo Curso:    ").append(inscripcion.getCodigoCurso()).append("\n");
        sb.append("Estudiante:      ").append(inscripcion.getEstudiante()).append("\n");
        sb.append("Email:           ").append(inscripcion.getEmailEstudiante()).append("\n");
        sb.append("Fecha Inscrip.:  ").append(
                inscripcion.getFechaInscripcion() != null
                        ? inscripcion.getFechaInscripcion().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                        : "N/A").append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("Estado:          ").append(inscripcion.getEstado()).append("\n");
        sb.append("-------------------------------------------\n");
        sb.append("\n");
        sb.append("===========================================\n");
        sb.append("Documento generado automaticamente\n");
        sb.append("Sistema de Gestion de Cursos Online\n");
        sb.append("Fecha: ").append(
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))).append("\n");
        sb.append("===========================================\n");
        return sb.toString();
    }
}
