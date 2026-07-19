package com.transportista.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class S3Service {

    private final S3Client s3Client;
    private final String bucketName;

    public String subirGuia(String transportista, String codigoGuia, byte[] contenido) {
        String ruta = String.format("cursos/%s/%d/%s/curso-%s.pdf",
                transportista.toLowerCase().replace(" ", "_"),
                LocalDateTime.now().getYear(),
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM")),
                codigoGuia);

        try {
            s3Client.putObject(PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(ruta)
                    .contentType("application/pdf")
                    .build(), RequestBody.fromBytes(contenido));

            String url = String.format("https://%s.s3.amazonaws.com/%s", bucketName, ruta);
            log.info("Curso subido a S3: {}", url);
            return url;
        } catch (S3Exception e) {
            log.error("Error al subir a S3: {}", e.awsErrorDetails().errorMessage());
            throw new RuntimeException("Error subiendo archivo a S3", e);
        }
    }

    public byte[] descargarGuia(String codigoGuia) {
        String prefijo = String.format("cursos/");
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefijo)
                        .build());

        for (S3Object obj : listResponse.contents()) {
            if (obj.key().contains(codigoGuia)) {
                try (ResponseInputStream<GetObjectResponse> response =
                             s3Client.getObject(GetObjectRequest.builder()
                                     .bucket(bucketName)
                                     .key(obj.key())
                                     .build());
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                    response.transferTo(baos);
                    log.info("Curso descargado de S3: {}", obj.key());
                    return baos.toByteArray();
                } catch (IOException e) {
                    throw new RuntimeException("Error descargando archivo de S3", e);
                }
            }
        }
        throw new RuntimeException("Archivo no encontrado en S3 para curso: " + codigoGuia);
    }

    public void eliminarGuia(String codigoGuia) {
        String prefijo = String.format("cursos/");
        ListObjectsV2Response listResponse = s3Client.listObjectsV2(
                ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefijo)
                        .build());

        for (S3Object obj : listResponse.contents()) {
            if (obj.key().contains(codigoGuia)) {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                        .bucket(bucketName)
                        .key(obj.key())
                        .build());
                log.info("Archivo eliminado de S3: {}", obj.key());
                return;
            }
        }
        log.warn("No se encontro archivo en S3 para curso: {}", codigoGuia);
    }

    public List<String> listarArchivos(String transportista) {
        String prefijo = String.format("cursos/%s/", transportista.toLowerCase().replace(" ", "_"));
        return s3Client.listObjectsV2(ListObjectsV2Request.builder()
                        .bucket(bucketName)
                        .prefix(prefijo)
                        .build())
                .contents()
                .stream()
                .map(S3Object::key)
                .collect(Collectors.toList());
    }
}
