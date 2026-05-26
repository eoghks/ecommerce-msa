package com.ecommerce.order.client;

import com.ecommerce.order.exception.ProductNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductClient {

    private final RestTemplate restTemplate;

    @Value("${service.product.url:http://localhost:8082}")
    private String productServiceUrl;

    /**
     * Product Service 에서 상품 정보 조회.
     * 주문 시점 가격·상품명 스냅샷 취득용.
     */
    public ProductInfo getProduct(Long productId) {
        String url = productServiceUrl + "/api/v1/products/" + productId;
        try {
            ResponseEntity<ProductInfo> response =
                    restTemplate.getForEntity(url, ProductInfo.class);
            return response.getBody();
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ProductNotFoundException(productId);
        } catch (Exception ex) {
            log.error("Product Service 조회 실패. productId={}", productId, ex);
            throw new ProductNotFoundException(productId);
        }
    }

    /** Product Service 응답 매핑 DTO */
    public record ProductInfo(
            Long id,
            String name,
            Long price,
            int stock
    ) {}
}
