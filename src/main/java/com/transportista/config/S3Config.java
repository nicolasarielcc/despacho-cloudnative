package com.transportista.config;

import com.transportista.service.S3Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Profile("!test")
public class S3Config {

    @Value("${cloud.aws.credentials.access-key:}")
    private String accessKey;

    @Value("${cloud.aws.credentials.secret-key:}")
    private String secretKey;

    @Value("${cloud.aws.credentials.session-token:}")
    private String sessionToken;

    @Value("${cloud.aws.region.static:us-east-1}")
    private String region;

    @Value("${cloud.aws.s3.bucket-name:cursos-grupo3-bucket}")
    private String bucketName;

    @Bean
    public S3Client s3Client() {
        if (sessionToken != null && !sessionToken.isBlank()) {
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsSessionCredentials.create(accessKey, secretKey, sessionToken)))
                    .build();
        }
        if (accessKey != null && !accessKey.isBlank()) {
            return S3Client.builder()
                    .region(Region.of(region))
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)))
                    .build();
        }
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public S3Service s3Service(S3Client s3Client) {
        return new S3Service(s3Client, bucketName);
    }
}
