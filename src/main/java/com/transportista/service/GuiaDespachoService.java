package com.transportista.service;

import com.transportista.dto.GuiaDespachoRequest;
import com.transportista.dto.GuiaDespachoResponse;
import com.transportista.entity.GuiaDespacho;
import com.transportista.entity.GuiaDespachoProcesada;
import com.transportista.enums.EstadoGuia;
import com.transportista.rabbitmq.GuiaProducer;
import com.transportista.repository.GuiaDespachoProcesadaRepository;
import com.transportista.repository.GuiaDespachoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class GuiaDespachoService {

    private final GuiaDespachoRepository guiaRepository;
    private final GuiaDespachoProcesadaRepository procesadaRepository;
    private final GuiaProducer guiaProducer;

    private static final DateTimeFormatter CODIGO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Transactional
    public GuiaDespachoResponse crearGuia(GuiaDespachoRequest request) {
        log.info("Creando nueva guia de despacho para transportista: {}", request.getTransportista());

        GuiaDespacho guia = GuiaDespacho.builder()
                .codigoGuia(generarCodigoGuia())
                .transportista(request.getTransportista())
                .fechaEmision(request.getFechaEmision())
                .origen(request.getOrigen())
                .destino(request.getDestino())
                .descripcionCarga(request.getDescripcionCarga())
                .pesoKg(request.getPesoKg())
                .estado(EstadoGuia.PENDIENTE)
                .build();

        GuiaDespacho saved = guiaRepository.save(guia);
        log.info("Guia guardada con id: {} y codigo: {}", saved.getId(), saved.getCodigoGuia());

        guiaProducer.enviarAColaPrincipal(saved);
        log.info("Guia {} enviada a cola principal", saved.getCodigoGuia());

        return toResponse(saved);
    }

    @Transactional
    public GuiaDespachoResponse modificarGuia(Long id, GuiaDespachoRequest request) {
        log.info("Modificando guia con id: {}", id);

        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guia no encontrada con id: " + id));

        guia.setTransportista(request.getTransportista());
        guia.setFechaEmision(request.getFechaEmision());
        guia.setOrigen(request.getOrigen());
        guia.setDestino(request.getDestino());
        guia.setDescripcionCarga(request.getDescripcionCarga());
        guia.setPesoKg(request.getPesoKg());
        guia.setEstado(EstadoGuia.PENDIENTE);

        GuiaDespacho updated = guiaRepository.save(guia);

        guiaProducer.enviarAColaPrincipal(updated);
        log.info("Guia {} reenviada a cola principal", updated.getCodigoGuia());

        return toResponse(updated);
    }

    @Transactional
    public void eliminarGuia(Long id) {
        log.info("Eliminando guia con id: {}", id);
        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guia no encontrada con id: " + id));
        guiaRepository.delete(guia);
        log.info("Guia {} eliminada correctamente", id);
    }

    @Transactional(readOnly = true)
    public List<GuiaDespachoResponse> consultarGuias(String transportista, String fechaStr) {
        log.info("Consultando guias. Transportista: {}, Fecha: {}", transportista, fechaStr);

        List<GuiaDespacho> guias;

        if (transportista != null && fechaStr != null) {
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            guias = guiaRepository.findByTransportistaAndFechaEmisionBetween(
                    transportista, fechaInicio, fechaFin);
        } else if (transportista != null) {
            guias = guiaRepository.findAll().stream()
                    .filter(g -> g.getTransportista().equalsIgnoreCase(transportista))
                    .collect(Collectors.toList());
        } else if (fechaStr != null) {
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            guias = guiaRepository.findAll().stream()
                    .filter(g -> !g.getFechaEmision().isBefore(fechaInicio)
                            && !g.getFechaEmision().isAfter(fechaFin))
                    .collect(Collectors.toList());
        } else {
            guias = guiaRepository.findAll();
        }

        return guias.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public GuiaDespachoResponse subirGuiaAS3(Long id) {
        log.info("Solicitando subida a S3 para guia id {}", id);
        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guia no encontrada con id: " + id));

        guiaProducer.enviarAColaPrincipal(guia);
        log.info("Guia {} reenviada a cola principal para procesar S3", guia.getCodigoGuia());

        return toResponse(guia);
    }

    @Transactional(readOnly = true)
    public GuiaDespachoResponse descargarGuia(Long id) {
        log.info("Descargando guia con id: {}", id);
        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guia no encontrada con id: " + id));
        return toResponse(guia);
    }

    @Transactional
    public List<GuiaDespachoProcesada> procesarColaYGuardar() {
        log.info("Procesando guias enviadas a tabla procesada");

        List<GuiaDespacho> guiasEnviadas = guiaRepository.findByEstado(EstadoGuia.ENVIADA);

        List<GuiaDespachoProcesada> procesadas = guiasEnviadas.stream()
                .map(guia -> {
                    GuiaDespachoProcesada procesada = GuiaDespachoProcesada.builder()
                            .codigoGuia(guia.getCodigoGuia())
                            .transportista(guia.getTransportista())
                            .fechaEmision(guia.getFechaEmision())
                            .origen(guia.getOrigen())
                            .destino(guia.getDestino())
                            .descripcionCarga(guia.getDescripcionCarga())
                            .pesoKg(guia.getPesoKg())
                            .urlS3(guia.getUrlS3())
                            .estado(guia.getEstado())
                            .fechaProcesamiento(LocalDateTime.now())
                            .build();
                    return procesadaRepository.save(procesada);
                })
                .collect(Collectors.toList());

        return procesadas;
    }

    @Transactional(readOnly = true)
    public GuiaDespachoResponse obtenerPorId(Long id) {
        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guia no encontrada con id: " + id));
        return toResponse(guia);
    }

    private String generarCodigoGuia() {
        String timestamp = LocalDateTime.now().format(CODIGO_FORMATTER);
        String secuencial = String.format("%03d", (int) (Math.random() * 1000));
        return "GD-" + timestamp + "-" + secuencial;
    }

    private GuiaDespachoResponse toResponse(GuiaDespacho guia) {
        return GuiaDespachoResponse.builder()
                .id(guia.getId())
                .codigoGuia(guia.getCodigoGuia())
                .transportista(guia.getTransportista())
                .fechaEmision(guia.getFechaEmision())
                .origen(guia.getOrigen())
                .destino(guia.getDestino())
                .descripcionCarga(guia.getDescripcionCarga())
                .pesoKg(guia.getPesoKg())
                .estado(guia.getEstado())
                .urlS3(guia.getUrlS3())
                .fechaCreacion(guia.getFechaCreacion())
                .build();
    }
}
