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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio principal para la gestión de guías de despacho.
 *
 * Contiene toda la lógica de negocio para CRUD de guías,
 * envío a colas RabbitMQ y procesamiento de colas.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GuiaDespachoService {

    private final GuiaDespachoRepository guiaRepository;
    private final GuiaDespachoProcesadaRepository procesadaRepository;
    private final GuiaProducer guiaProducer;

    private static final DateTimeFormatter CODIGO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    // ================================================================
    // CREAR GUÍA DE DESPACHO
    // Endpoint: POST /api/guias
    // ================================================================

    /**
     * Crea una nueva guía de despacho.
     * Genera el código de guía automáticamente con el formato GD-{año}{mes}{dia}{hora}{min}-{nro}
     * La guía se crea con estado PENDIENTE y luego se envía a la cola RabbitMQ.
     *
     * Si el envío a la cola falla, la guía se actualiza a CON_ERROR y se envía a la cola de errores.
     */
    @Transactional
    public GuiaDespachoResponse crearGuia(GuiaDespachoRequest request) {
        log.info("Creando nueva guía de despacho para transportista: {}", request.getTransportista());

        // Construir entidad desde el DTO
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

        // Guardar en base de datos
        GuiaDespacho saved = guiaRepository.save(guia);
        log.info("Guía guardada con id: {} y código: {}", saved.getId(), saved.getCodigoGuia());

        // Enviar a la cola RabbitMQ (cola-guias-exitosas)
        // Si falla, se captura la excepción y se envía a la cola de errores
        try {
            guiaProducer.enviarGuiaExitosa(saved);
            saved.setEstado(EstadoGuia.ENVIADA);
            guiaRepository.save(saved);
            log.info("Guía {} enviada exitosamente a cola-guias-exitosas", saved.getCodigoGuia());
        } catch (Exception e) {
            log.error("Error al enviar guía {} a la cola de exitosas: {}", saved.getCodigoGuia(), e.getMessage());
            saved.setEstado(EstadoGuia.CON_ERROR);
            guiaRepository.save(saved);
            // Enviar a la cola de errores (cola-guias-error)
            try {
                guiaProducer.enviarGuiaError(saved);
                log.info("Guía {} enviada a cola-guias-error", saved.getCodigoGuia());
            } catch (Exception ex) {
                log.error("Error crítico: no se pudo enviar guía {} a cola de errores: {}", saved.getCodigoGuia(), ex.getMessage());
            }
        }

        return toResponse(saved);
    }

    // ================================================================
    // MODIFICAR / ACTUALIZAR GUÍA
    // Endpoint: PUT /api/guias/{id}
    // ================================================================

    /**
     * Modifica una guía de despacho existente.
     * Solo se pueden modificar guías en estado PENDIENTE o CON_ERROR.
     */
    @Transactional
    public GuiaDespachoResponse modificarGuia(Long id, GuiaDespachoRequest request) {
        log.info("Modificando guía con id: {}", id);

        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guía no encontrada con id: " + id));

        guia.setTransportista(request.getTransportista());
        guia.setFechaEmision(request.getFechaEmision());
        guia.setOrigen(request.getOrigen());
        guia.setDestino(request.getDestino());
        guia.setDescripcionCarga(request.getDescripcionCarga());
        guia.setPesoKg(request.getPesoKg());
        guia.setEstado(EstadoGuia.PENDIENTE);

        GuiaDespacho updated = guiaRepository.save(guia);

        // Reenviar a la cola
        try {
            guiaProducer.enviarGuiaExitosa(updated);
            updated.setEstado(EstadoGuia.ENVIADA);
            guiaRepository.save(updated);
        } catch (Exception e) {
            log.error("Error al reenviar guía {}: {}", updated.getCodigoGuia(), e.getMessage());
            updated.setEstado(EstadoGuia.CON_ERROR);
            guiaRepository.save(updated);
            try {
                guiaProducer.enviarGuiaError(updated);
            } catch (Exception ex) {
                log.error("Error crítico al enviar a cola de errores: {}", ex.getMessage());
            }
        }

        return toResponse(updated);
    }

    // ================================================================
    // ELIMINAR GUÍA ESPECÍFICA
    // Endpoint: DELETE /api/guias/{id}
    // ================================================================

    /**
     * Elimina una guía de despacho por su ID.
     */
    @Transactional
    public void eliminarGuia(Long id) {
        log.info("Eliminando guía con id: {}", id);

        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guía no encontrada con id: " + id));

        guiaRepository.delete(guia);
        log.info("Guía {} eliminada correctamente", id);
    }

    // ================================================================
    // CONSULTAR GUÍAS POR TRANSPORTISTA Y FECHA
    // Endpoint: GET /api/guias?transportista={t}&fecha={f}
    // ================================================================

    /**
     * Consulta guías de despacho por transportista y fecha.
     *
     * Si solo se proporciona transportista, devuelve todas sus guías.
     * Si solo se proporciona fecha, devuelve las guías de ese día.
     * Si se proporcionan ambos, filtra por ambos criterios.
     *
     * @param transportista nombre del transportista (opcional)
     * @param fechaStr      fecha en formato "yyyy-MM-dd" (opcional)
     */
    @Transactional(readOnly = true)
    public List<GuiaDespachoResponse> consultarGuias(String transportista, String fechaStr) {
        log.info("Consultando guías. Transportista: {}, Fecha: {}", transportista, fechaStr);

        List<GuiaDespacho> guias;

        if (transportista != null && fechaStr != null) {
            // Ambos filtros
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            guias = guiaRepository.findByTransportistaAndFechaEmisionBetween(
                    transportista, fechaInicio, fechaFin);
        } else if (transportista != null) {
            // Solo por transportista (búsqueda por nombre exacto)
            guias = guiaRepository.findAll().stream()
                    .filter(g -> g.getTransportista().equalsIgnoreCase(transportista))
                    .collect(Collectors.toList());
        } else if (fechaStr != null) {
            // Solo por fecha
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            guias = guiaRepository.findAll().stream()
                    .filter(g -> !g.getFechaEmision().isBefore(fechaInicio) && !g.getFechaEmision().isAfter(fechaFin))
                    .collect(Collectors.toList());
        } else {
            // Sin filtros = todas las guías
            guias = guiaRepository.findAll();
        }

        return guias.stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // ================================================================
    // SUBIR GUÍA A S3
    // Endpoint: POST /api/guias/{id}/subir-s3
    // ================================================================

    /**
     * Sube una guía de despacho a S3.
     *
     * IMPORTANTE: El método real de subida a S3 se implementa en S3Service.
     * Aquí se deja como placeholder. El archivo se genera (PDF o XML) y se sube.
     *
     * La URL resultante se guarda en el campo urlS3 de la entidad.
     */
    @Transactional
    public GuiaDespachoResponse subirGuiaAS3(Long id) {
        log.info("Subiendo guía id {} a S3", id);

        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guía no encontrada con id: " + id));

        // TODO: Implementar subida real a S3 cuando se configure S3Config
        // String urlS3 = s3Service.subirGuia(guia.getCodigoGuia(), guia.getTransportista(), generarPdf(guia));
        // guia.setUrlS3(urlS3);

        // Por ahora, simulamos la URL de S3
        String urlS3 = String.format("s3://<S3_BUCKET_NAME>/guias/%s/%s/guia-%s.pdf",
                guia.getTransportista().toLowerCase().replace(" ", "_"),
                guia.getFechaEmision().getYear(),
                guia.getCodigoGuia());
        guia.setUrlS3(urlS3);

        guiaRepository.save(guia);
        log.info("Guía {} subida a S3: {}", guia.getCodigoGuia(), urlS3);

        return toResponse(guia);
    }

    // ================================================================
    // DESCARGAR GUÍA (CON VALIDACIÓN DE PERMISOS)
    // Endpoint: GET /api/guias/{id}/descargar
    // ================================================================

    /**
     * Descarga una guía desde S3.
     *
     * Este endpoint está protegido por el rol "consulta" en SecurityConfig.
     * Solo usuarios con rol "consulta" pueden descargar guías.
     *
     * @return información de la guía para descarga
     */
    @Transactional(readOnly = true)
    public GuiaDespachoResponse descargarGuia(Long id) {
        log.info("Descargando guía con id: {}", id);

        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guía no encontrada con id: " + id));

        // TODO: Implementar descarga real desde S3 cuando se configure S3Service
        // byte[] contenido = s3Service.descargarGuia(guia.getCodigoGuia());

        return toResponse(guia);
    }

    // ================================================================
    // ENDPOINT ADICIONAL: PROCESAR COLA 1 Y GUARDAR EN TABLA NUEVA
    // Endpoint: POST /api/cola/procesar-guias
    // ================================================================

    /**
     * Consume los mensajes de la cola-guias-exitosas y los guarda
     * en la tabla GUIA_DESPACHO_PROCESADA (tabla NUEVA, distinta a las anteriores).
     *
     * Este método es llamado por un endpoint adicional y procesa
     * todas las guías en estado ENVIADA que aún no han sido procesadas.
     */
    @Transactional
    public List<GuiaDespachoProcesada> procesarColaYGuardar() {
        log.info("Procesando cola-guias-exitosas y guardando en tabla nueva");

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

        log.info("Procesadas {} guías en tabla GUIA_DESPACHO_PROCESADA", procesadas.size());

        return procesadas;
    }

    // ================================================================
    // MÉTODOS AUXILIARES
    // ================================================================

    /**
     * Obtiene una guía por su ID.
     */
    @Transactional(readOnly = true)
    public GuiaDespachoResponse obtenerPorId(Long id) {
        GuiaDespacho guia = guiaRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Guía no encontrada con id: " + id));
        return toResponse(guia);
    }

    /**
     * Genenera un código único para la guía.
     * Formato: GD-{timestamp}-{nroAleatorio}
     * Ejemplo: GD-202501151430-001
     */
    private String generarCodigoGuia() {
        String timestamp = LocalDateTime.now().format(CODIGO_FORMATTER);
        String secuencial = String.format("%03d", (int) (Math.random() * 1000));
        return "GD-" + timestamp + "-" + secuencial;
    }

    /**
     * Convierte una entidad GuiaDespacho a DTO de respuesta.
     */
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
