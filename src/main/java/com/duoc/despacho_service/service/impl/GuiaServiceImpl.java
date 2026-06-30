package com.duoc.despacho_service.service.impl;

import com.duoc.despacho_service.dto.request.GuiaRequestDTO;
import com.duoc.despacho_service.dto.response.GuiaResponseDTO;
import com.duoc.despacho_service.entity.GuiaDespacho;
import com.duoc.despacho_service.enums.EstadoGuia;
import com.duoc.despacho_service.repository.GuiaDespachoRepository;
import com.duoc.despacho_service.service.GuiaService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityNotFoundException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Service
public class GuiaServiceImpl implements GuiaService {

    private static final DateTimeFormatter DATE_FOLDER_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final String DESCARGA_ROLE = "descarga";

    private final GuiaDespachoRepository guiaDespachoRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;
    private final Path storagePath;
    private final String bucketName;

    public GuiaServiceImpl(
            GuiaDespachoRepository guiaDespachoRepository,
            S3Client s3Client,
            ObjectMapper objectMapper,
            @Value("${storage.efs.path:target/efs/guias}") String storagePath,
            @Value("${aws.s3.bucket-name:}") String bucketName) {
        this.guiaDespachoRepository = guiaDespachoRepository;
        this.s3Client = s3Client;
        this.objectMapper = objectMapper;
        this.storagePath = Path.of(storagePath);
        this.bucketName = bucketName;
    }

    @Override
    @Transactional
    public GuiaResponseDTO crearGuiaTemporal(GuiaRequestDTO guiaRequestDTO) {
        if (guiaDespachoRepository.findByNumeroGuia(guiaRequestDTO.getNumeroGuia()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe una guía con ese número");
        }

        GuiaDespacho guia = new GuiaDespacho();
        mapRequestToEntity(guiaRequestDTO, guia);
        guia.setEstado(guiaRequestDTO.getEstado() != null ? guiaRequestDTO.getEstado() : EstadoGuia.PENDIENTE);
        guia.setRutaS3(null);

        GuiaDespacho saved = guiaDespachoRepository.save(guia);
        Path archivoPdf = buildLocalPdfPath(saved);

        try {
            Files.createDirectories(storagePath);
            generatePdf(saved, archivoPdf);
            saved.setRutaEfs(archivoPdf.toString());
            saved = guiaDespachoRepository.save(saved);
            return toResponse(saved);
        } catch (IOException ex) {
            safeDeleteFile(archivoPdf);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible generar la guía temporal", ex);
        }
    }

    @Override
    @Transactional
    public GuiaResponseDTO subirAS3(Long id) {
        GuiaDespacho guia = getGuiaOrThrow(id);
        Path archivoPdf = resolveLocalPdfPath(guia);
        if (!Files.exists(archivoPdf)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No existe el archivo temporal en EFS");
        }
        ensureBucketConfigured();

        String s3Key = buildS3Key(guia);
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(s3Key)
                        .contentType("application/pdf")
                        .build(),
                RequestBody.fromFile(archivoPdf));

        guia.setRutaS3(s3Key);
        guia.setEstado(EstadoGuia.ENVIADO);
        return toResponse(guiaDespachoRepository.save(guia));
    }

    @Override
    public byte[] descargarGuiaConPermisos(Long id, String rol) {
        if (!DESCARGA_ROLE.equalsIgnoreCase(normalizeRole(rol))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes permisos para descargar esta guía");
        }

        GuiaDespacho guia = getGuiaOrThrow(id);
        Path archivoPdf = resolveLocalPdfPath(guia);
        if (Files.exists(archivoPdf)) {
            try {
                return Files.readAllBytes(archivoPdf);
            } catch (IOException ex) {
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible leer la guía temporal", ex);
            }
        }

        if (StringUtils.hasText(guia.getRutaS3())) {
            ensureBucketConfigured();
            try {
                return s3Client.getObjectAsBytes(
                        GetObjectRequest.builder()
                                .bucket(bucketName)
                                .key(guia.getRutaS3())
                                .build()).asByteArray();
            } catch (NoSuchKeyException ex) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La guía no existe en S3", ex);
            }
        }

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "La guía no tiene archivo temporal ni copia en S3");
    }

    @Override
    @Transactional
    public GuiaResponseDTO actualizarGuia(Long id, GuiaRequestDTO guiaRequestDTO) {
        GuiaDespacho guia = getGuiaOrThrow(id);
        Path archivoPdfAnterior = resolveLocalPdfPath(guia);
        String rutaS3Anterior = guia.getRutaS3();

        mapRequestToEntity(guiaRequestDTO, guia);
        guia.setEstado(guiaRequestDTO.getEstado() != null ? guiaRequestDTO.getEstado() : guia.getEstado());
        guia.setRutaS3(null);

        try {
            deleteS3ObjectIfNeeded(rutaS3Anterior);
            safeDeleteFile(archivoPdfAnterior);

            GuiaDespacho saved = guiaDespachoRepository.save(guia);
            Path nuevoArchivoPdf = buildLocalPdfPath(saved);
            Files.createDirectories(storagePath);
            generatePdf(saved, nuevoArchivoPdf);
            saved.setRutaEfs(nuevoArchivoPdf.toString());
            saved = guiaDespachoRepository.save(saved);
            return toResponse(saved);
        } catch (IOException ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No fue posible actualizar la guía", ex);
        }
    }

    @Override
    @Transactional
    public void eliminarGuia(Long id) {
        GuiaDespacho guia = getGuiaOrThrow(id);
        safeDeleteFile(resolveLocalPdfPath(guia));
        deleteS3ObjectIfNeeded(guia.getRutaS3());
        guiaDespachoRepository.delete(guia);
    }

    @Override
    public List<GuiaResponseDTO> buscarPorTransportistaYFecha(String transportista, LocalDate fecha) {
        return guiaDespachoRepository.findByTransportistaAndFecha(transportista, fecha)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    private GuiaDespacho getGuiaOrThrow(Long id) {
        return guiaDespachoRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("No existe la guía con id " + id));
    }

    private void mapRequestToEntity(GuiaRequestDTO request, GuiaDespacho guia) {
        guia.setNumeroGuia(request.getNumeroGuia());
        guia.setTransportista(request.getTransportista());
        guia.setFecha(request.getFecha());
        guia.setDireccionOrigen(request.getDireccionOrigen());
        guia.setDireccionDestino(request.getDireccionDestino());
        guia.setDescripcionCarga(request.getDescripcionCarga());
        guia.setEstado(request.getEstado());
    }

    private GuiaResponseDTO toResponse(GuiaDespacho guia) {
        GuiaResponseDTO response = new GuiaResponseDTO();
        response.setId(guia.getId());
        response.setNumeroGuia(guia.getNumeroGuia());
        response.setTransportista(guia.getTransportista());
        response.setFecha(guia.getFecha());
        response.setDireccionOrigen(guia.getDireccionOrigen());
        response.setDireccionDestino(guia.getDireccionDestino());
        response.setDescripcionCarga(guia.getDescripcionCarga());
        response.setEstado(guia.getEstado());
        response.setRutaEfs(guia.getRutaEfs());
        response.setRutaS3(guia.getRutaS3());
        return response;
    }

    private Path buildLocalPdfPath(GuiaDespacho guia) {
        String fileName = "guia-" + guia.getId() + "-" + sanitizeFilePart(guia.getNumeroGuia()) + ".pdf";
        return storagePath.resolve(fileName);
    }

    private Path resolveLocalPdfPath(GuiaDespacho guia) {
        if (StringUtils.hasText(guia.getRutaEfs())) {
            return Path.of(guia.getRutaEfs());
        }
        return buildLocalPdfPath(guia);
    }

    private String buildS3Key(GuiaDespacho guia) {
        String fechaFolder = guia.getFecha().format(DATE_FOLDER_FORMAT);
        String transportistaFolder = sanitizePathPart(guia.getTransportista());
        String fileName = "guia-" + sanitizeFilePart(guia.getNumeroGuia()) + ".pdf";
        return fechaFolder + "/" + transportistaFolder + "/" + fileName;
    }

    private void generatePdf(GuiaDespacho guia, Path filePath) throws IOException {
        Files.createDirectories(filePath.getParent());
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDType1Font font = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            try (PDPageContentStream contentStream = new PDPageContentStream(document, page)) {
                contentStream.beginText();
                contentStream.setFont(font, 12);
                contentStream.setLeading(16f);
                contentStream.newLineAtOffset(50, 780);
                writePdfLine(contentStream, "Guia de despacho");
                writePdfLine(contentStream, "Numero guia: " + guia.getNumeroGuia());
                writePdfLine(contentStream, "Transportista: " + guia.getTransportista());
                writePdfLine(contentStream, "Fecha: " + guia.getFecha());
                writePdfLine(contentStream, "Origen: " + guia.getDireccionOrigen());
                writePdfLine(contentStream, "Destino: " + guia.getDireccionDestino());
                writePdfLine(contentStream, "Descripcion: " + nullSafe(guia.getDescripcionCarga()));
                writePdfLine(contentStream, "Estado: " + guia.getEstado());
                writePdfLine(contentStream, "Ruta EFS: " + nullSafe(guia.getRutaEfs()));
                writePdfLine(contentStream, "Ruta S3: " + nullSafe(guia.getRutaS3()));
                contentStream.endText();
            }
            document.save(filePath.toFile());
        }
    }

    private void writePdfLine(PDPageContentStream contentStream, String value) throws IOException {
        contentStream.showText(sanitizePdfText(value));
        contentStream.newLine();
    }

    private String sanitizePdfText(String value) {
        String normalized = Normalizer.normalize(nullSafe(value), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.chars()
                .mapToObj(character -> character >= 32 && character <= 126 ? String.valueOf((char) character) : "?")
                .collect(Collectors.joining());
    }

    private String sanitizeFilePart(String value) {
        return sanitizePathPart(value).replace('/', '_');
    }

    private String sanitizePathPart(String value) {
        return sanitizePdfText(value)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String normalizeRole(String rol) {
        return rol == null ? "" : rol.trim().toUpperCase(Locale.ROOT);
    }

    private void ensureBucketConfigured() {
        if (!StringUtils.hasText(bucketName)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "AWS S3 bucket no configurado");
        }
    }

    private void deleteS3ObjectIfNeeded(String key) {
        if (!StringUtils.hasText(key) || !StringUtils.hasText(bucketName)) {
            return;
        }
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucketName).key(key).build());
        } catch (Exception ignored) {
            // Si la copia en S3 ya no existe o no se puede borrar, no bloqueamos el flujo local.
        }
    }

    private void safeDeleteFile(Path filePath) {
        try {
            Files.deleteIfExists(filePath);
        } catch (IOException ignored) {
            // Dejar el archivo temporal no bloquea el flujo principal; se puede limpiar después.
        }
    }
}
