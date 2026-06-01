package com.ecommerce.product.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint:http://localhost:9000}")
    private String endpoint;

    @Value("${minio.access-key:minioadmin}")
    private String accessKey;

    @Value("${minio.secret-key:minioadmin}")
    private String secretKey;

    @Value("${minio.bucket:product-images}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    /** 애플리케이션 기동 시 버킷 생성 + 퍼블릭 읽기 정책 적용 */
    @PostConstruct
    public void initBucket() {
        try {
            MinioClient client = minioClient();
            boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
            if (!exists) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                log.info("MinIO 버킷 생성: {}", bucket);
            }
            // 익명 읽기 허용 — 브라우저에서 직접 이미지 접근 가능
            String policy = """
                    {
                      "Version":"2012-10-17",
                      "Statement":[{
                        "Effect":"Allow",
                        "Principal":{"AWS":["*"]},
                        "Action":["s3:GetObject"],
                        "Resource":["arn:aws:s3:::%s/*"]
                      }]
                    }
                    """.formatted(bucket);
            client.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(policy).build());
            log.info("MinIO 버킷 퍼블릭 읽기 정책 적용: {}", bucket);
        } catch (Exception e) {
            log.warn("MinIO 버킷 초기화 실패 (서비스 기동은 계속됨): {}", e.getMessage());
        }
    }
}
