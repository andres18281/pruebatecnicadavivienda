package com.techdocs.worker.infrastructure;

import com.techdocs.worker.application.FileStoragePort;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;

@Component
public class MinioStorageAdapter implements FileStoragePort {

    private final MinioClient minioClient;
    private final String bucket;

    public MinioStorageAdapter(MinioClient minioClient, @Value("${S3_BUCKET}") String bucket) {
        this.minioClient = minioClient;
        this.bucket = bucket;
    }

    @Override
    public byte[] get(String key) {
        try (InputStream input = minioClient.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
            return input.readAllBytes();
        } catch (Exception exception) {
            throw new IllegalStateException("No se pudo descargar el archivo " + key, exception);
        }
    }
}
