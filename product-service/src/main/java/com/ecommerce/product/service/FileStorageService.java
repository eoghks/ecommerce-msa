package com.ecommerce.product.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private final MinioClient minioClient;

    @Value("${minio.bucket:product-images}")
    private String bucket;

    @Value("${minio.public-url:http://localhost:9000}")
    private String publicUrl;

    /**
     * 이미지 파일을 MinIO에 업로드하고 퍼블릭 URL을 반환한다.
     * 파일명: UUID + 원본 확장자 (중복 방지)
     */
    public String uploadImage(MultipartFile file) {
        validateImageFile(file);
        String filename = UUID.randomUUID() + getExtension(file.getOriginalFilename());
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(filename)
                            .stream(file.getInputStream(), file.getSize(), -1)
                            .contentType(file.getContentType())
                            .build()
            );
            String url = publicUrl + "/" + bucket + "/" + filename;
            log.info("이미지 업로드 완료: {}", url);
            return url;
        } catch (Exception e) {
            log.error("MinIO 업로드 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("이미지 업로드에 실패했습니다.");
        }
    }

    /** 스트림에서 직접 MinIO 업로드 (마이그레이션용) */
    public String uploadStream(java.io.InputStream stream, String filename, String contentType) {
        try {
            minioClient.putObject(
                    io.minio.PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(filename)
                            .stream(stream, -1, 10 * 1024 * 1024)
                            .contentType(contentType)
                            .build()
            );
            return publicUrl + "/" + bucket + "/" + filename;
        } catch (Exception e) {
            log.error("MinIO 스트림 업로드 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("이미지 업로드에 실패했습니다.");
        }
    }

    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드 가능합니다.");
        }
        if (file.getSize() > 10 * 1024 * 1024) {
            throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
        }
    }

    private String getExtension(String filename) {
        if (filename == null || !filename.contains(".")) return ".jpg";
        return filename.substring(filename.lastIndexOf('.'));
    }
}
