package com.techdocs.document.infrastructure.storage;

import com.techdocs.document.application.ports.out.FileStoragePort;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Component
public class MinioStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageAdapter(MinioClient minioClient, @Value("${S3_BUCKET}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public void save(String key, byte[] content, String mimeType) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(bucket)
                    .object(key)
                    .stream(new ByteArrayInputStream(content), content.length, -1)
                    .contentType(mimeType)
                    .build());
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo guardar el archivo en el bucket.", exception);
        }
    }
}
