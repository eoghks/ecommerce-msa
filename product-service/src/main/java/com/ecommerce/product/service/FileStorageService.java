package com.ecommerce.product.service;

import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.UUID;

/**
 * 상품 이미지 업로드 (MinIO).
 *
 * C-4 보안: Content-Type 헤더는 클라이언트가 위조 가능하므로 신뢰하지 않는다.
 *   - 파일 매직넘버(시그니처)로 실제 타입을 판별하고 화이트리스트(JPEG/PNG/GIF/WebP)만 허용.
 *   - 저장 시 Content-Type을 판별된 값으로 강제, 파일명·확장자도 판별 결과로 생성
 *     → HTML/JS/SVG 등 스크립트 내장 파일의 image 위장 업로드 → 저장형 XSS 차단.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileStorageService {

    private static final long MAX_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;

    @Value("${minio.bucket:product-images}")
    private String bucket;

    @Value("${minio.public-url:http://localhost:9000}")
    private String publicUrl;

    public String uploadImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("파일이 비어있습니다.");
        }
        if (file.getSize() > MAX_SIZE) {
            throw new IllegalArgumentException("파일 크기는 10MB를 초과할 수 없습니다.");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("업로드 파일 읽기 실패: {}", e.getMessage());
            throw new IllegalStateException("파일을 읽을 수 없습니다.");
        }

        // 매직넘버로 실제 타입 판별 — 허용되지 않으면 예외
        ImageType type = detectImageType(bytes);
        String filename = UUID.randomUUID() + type.extension();

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(filename)
                            .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                            .contentType(type.contentType())   // 검증된 타입 강제(클라 헤더 무시)
                            .build()
            );
            String url = publicUrl + "/" + bucket + "/" + filename;
            log.info("이미지 업로드 완료: {} ({})", url, type.contentType());
            return url;
        } catch (Exception e) {
            log.error("MinIO 업로드 실패: {}", e.getMessage(), e);
            throw new IllegalStateException("이미지 업로드에 실패했습니다.");
        }
    }

    /** 파일 시그니처(매직넘버)로 실제 이미지 타입 판별. 미허용이면 예외. */
    private ImageType detectImageType(byte[] b) {
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return new ImageType("image/jpeg", ".jpg");
        }
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return new ImageType("image/png", ".png");
        }
        if (b.length >= 6
                && b[0] == 'G' && b[1] == 'I' && b[2] == 'F' && b[3] == '8'
                && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return new ImageType("image/gif", ".gif");
        }
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return new ImageType("image/webp", ".webp");
        }
        throw new IllegalArgumentException(
                "허용되지 않는 이미지 형식입니다. JPEG/PNG/GIF/WebP만 업로드할 수 있습니다.");
    }

    private record ImageType(String contentType, String extension) {}
}
