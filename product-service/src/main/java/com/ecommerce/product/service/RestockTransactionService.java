package com.ecommerce.product.service;

import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 복구 트랜잭션 서비스 — RestockService에서 분리.
 * (StockDecreaseTransactionService와 동일하게 self-invocation 트랜잭션 문제 회피)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RestockTransactionService {

    private final ProductRepository productRepository;

    @Transactional
    public void increaseStock(Long productId, int quantity) {
        // 원자적 복구 (취소 보상)
        int updated = productRepository.increaseStockAtomic(productId, quantity);
        if (updated == 0) {
            throw new ProductNotFoundException(productId);
        }
        log.info("재고 복구 완료. productId={}, +{}", productId, quantity);
    }
}
