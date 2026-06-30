package com.ecommerce.product.service;

import com.ecommerce.product.domain.Product;
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
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
        product.increaseStock(quantity);  // dirty check 자동 flush
        log.info("재고 복구 완료. productId={}, +{}", productId, quantity);
    }
}
