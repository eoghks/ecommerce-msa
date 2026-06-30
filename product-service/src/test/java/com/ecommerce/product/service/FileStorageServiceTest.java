package com.ecommerce.product.service;

import io.minio.MinioClient;
import io.minio.ObjectWriteResponse;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileStorageService 단위 테스트 (업로드 콘텐츠 검증)")
class FileStorageServiceTest {

    @Mock private MinioClient minioClient;
    @InjectMocks private FileStorageService fileStorageService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(fileStorageService, "bucket", "product-images");
        ReflectionTestUtils.setField(fileStorageService, "publicUrl", "http://localhost:9000");
    }

    private byte[] pngBytes() {
        // PNG 시그니처 + 더미
        return new byte[]{(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3, 4};
    }

    @Test
    @DisplayName("정상 PNG — 시그니처 통과, 검증된 content-type으로 업로드")
    void uploadImage_validPng() throws Exception {
        given(minioClient.putObject(any(PutObjectArgs.class))).willReturn((ObjectWriteResponse) null);
        MockMultipartFile file = new MockMultipartFile(
                "file", "x.png", "image/png", pngBytes());

        String url = fileStorageService.uploadImage(file);

        assertThat(url).startsWith("http://localhost:9000/product-images/").endsWith(".png");
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("image/png 위장한 HTML — 매직넘버 불일치로 거부 (저장형 XSS 차단)")
    void uploadImage_htmlDisguisedAsPng_rejected() throws Exception {
        byte[] html = "<html><script>alert(1)</script></html>".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "evil.png", "image/png", html);  // Content-Type은 image/png로 위장

        assertThatThrownBy(() -> fileStorageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("허용되지 않는 이미지 형식");
        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    @DisplayName("SVG — 차단 (스크립트 내장 가능)")
    void uploadImage_svg_rejected() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>x</script></svg>".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "a.svg", "image/svg+xml", svg);

        assertThatThrownBy(() -> fileStorageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("빈 파일 — 거부")
    void uploadImage_empty_rejected() {
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", new byte[0]);
        assertThatThrownBy(() -> fileStorageService.uploadImage(file))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("비어");
    }
}
