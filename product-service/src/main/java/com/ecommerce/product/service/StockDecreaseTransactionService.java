package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderCreatedPayload;
import com.ecommerce.product.exception.ProductNotFoundException;
import com.ecommerce.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고 차감 트랜잭션 서비스 — StockDecreaseService에서 분리.
 *
 * 분리 이유:
 *   StockDecreaseService.doDecreaseStock() 이 self-invocation 으로 호출되면
 *   Spring AOP 프록시를 거치지 않아 @Transactional 이 적용되지 않음.
 *   별도 Bean 으로 분리하면 프록시를 통해 호출되므로 트랜잭션 보장.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StockDecreaseTransactionService {

    private final ProductRepository productRepository;

    /**
     * 트랜잭션 내 재고 차감.
     * 재고 부족 시 IllegalStateException 발생 → 호출부(StockDecreaseService)에서 보상 처리.
     */
    @Transactional
    public void decreaseStock(OrderCreatedPayload payload) {
        for (OrderCreatedPayload.Item item : payload.items()) {
            com.ecommerce.product.domain.Product product = productRepository
                    .findById(item.productId())
                    .orElseThrow(() -> new ProductNotFoundException(item.productId()));
            product.decreaseStock(item.quantity());  // 재고 부족 시 IllegalStateException
            // dirty check 으로 자동 flush — 별도 save() 불필요
        }
        log.info("재고 차감 완료. orderId={}", payload.orderId());
    }
}
