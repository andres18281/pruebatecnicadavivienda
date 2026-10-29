package com.techdocs.document.infrastructure.storage;

import com.techdocs.document.application.ports.out.FileStoragePort;
import com.techdocs.resilience.CircuitGuard;
import com.techdocs.resilience.OpenCircuitException;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
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
    public void save(String key, byte[] content, String mimeType) {
        try {
            minio.call(() -> {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(bucket)
                        .object(key)
                        .stream(new ByteArrayInputStream(content), content.length, -1)
                        .contentType(mimeType)
                        .build());
                return null;
            });
        } catch (OpenCircuitException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo guardar el archivo en el bucket.", exception);
        }
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
            throw new IllegalStateException("No se pudo leer el archivo " + key, exception);
        }
    }
}
