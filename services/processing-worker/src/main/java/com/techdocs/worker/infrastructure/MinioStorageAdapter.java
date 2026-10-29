package com.techdocs.worker.infrastructure;

import com.techdocs.resilience.CircuitGuard;
import com.techdocs.resilience.OpenCircuitException;
import com.techdocs.worker.application.FileStoragePort;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;
    private final CircuitGuard minio;
    private final String bucket;

    public MinioStorageAdapter(
            MinioClient minioClient,
            CircuitBreakerRegistry circuitBreakers,
            @Value("${S3_BUCKET}") String bucket
    ) {
        this.minioClient = minioClient;
        this.minio = CircuitGuard.of(circuitBreakers, "minio");
        this.bucket = bucket;
    }

    @Override
    public byte[] get(String key) {
        try {
            return minio.call(() -> {
                try (InputStream input = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                    return input.readAllBytes();
                }
            });
        } catch (OpenCircuitException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo descargar el archivo " + key, exception);
        }
    }
}
