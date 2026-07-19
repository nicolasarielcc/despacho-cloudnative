package com.transportista.config;

import com.transportista.service.S3Service;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.Mockito.mock;

@Configuration
@Profile("test")
public class TestS3Config {

    @Bean
    public S3Client testS3Client() {
        return mock(S3Client.class);
    }

    @Bean
    public S3Service testS3Service(S3Client s3Client) {
        return new S3Service(s3Client, "test-bucket");
    }
}
