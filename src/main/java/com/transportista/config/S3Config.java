package com.transportista.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración de AWS S3.
 *
 * IMPORTANTE: Esta clase es un placeholder. La integración real con S3
 * se hará cuando se tengan las credenciales de AWS Academy.
 *
 * Para activar S3, debes:
 * 1. Agregar las credenciales en application.yml:
 *    cloud.aws.credentials.access-key=<AWS_ACCESS_KEY_ID>
 *    cloud.aws.credentials.secret-key=<AWS_SECRET_ACCESS_KEY>
 *    cloud.aws.credentials.session-token=<AWS_SESSION_TOKEN>
 *    cloud.aws.region.static=us-east-1
 *    cloud.aws.s3.bucket-name=<nombre-del-bucket>
 *
 * 2. Descomentar la dependencia en pom.xml (spring-cloud-aws-starter-s3)
 *    y la configuración en application.yml.
 *
 * 3. Crear la clase S3Service que use S3Client para subir/descargar archivos.
 *
 * Los valores de AWS se obtienen de:
 *   AWS Academy → Learner Lab → AWS Details → AWS CLI
 *   Copiar: AWS_ACCESS_KEY_ID, AWS_SECRET_ACCESS_KEY, AWS_SESSION_TOKEN
 *
 * RECUERDA: Estos valores CAMBIAN cada vez que inicias sesión en AWS Academy.
 */
@Slf4j
@Configuration
public class S3Config {

    // TODO: Descomentar cuando se configure AWS

    /*
    @Value("${cloud.aws.s3.bucket-name}")
    private String bucketName;

    @Value("${cloud.aws.region.static}")
    private String region;

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .build();
    }

    @Bean
    public S3Service s3Service(S3Client s3Client) {
        return new S3Service(s3Client, bucketName);
    }
    */
}
