package com.ecommerce.product.service;

import com.ecommerce.product.event.OrderItemCancelledPayload;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
@DisplayName("RestockService 단위 테스트")
class RestockServiceTest {

    @Mock private RedisTemplate<String, String> redisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private RestockTransactionService restockTransactionService;

    @InjectMocks private RestockService restockService;

    private OrderItemCancelledPayload payload() {
        return new OrderItemCancelledPayload(1L, 10L, 100L, 2);
    }

    @Test
    @DisplayName("정상 — 락 획득 후 재고 복구 + 멱등 키 설정")
    void restock_success() throws InterruptedException {
        given(redisTemplate.hasKey("stock:restocked:item:10")).willReturn(false);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(redisTemplate.opsForValue()).willReturn(valueOperations);

        restockService.restock(payload());

        verify(restockTransactionService).increaseStock(100L, 2);
        verify(valueOperations).set(eq("stock:restocked:item:10"), eq("1"), any());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("멱등 — 이미 복구된 항목이면 재고 증가 안 함")
    void restock_alreadyProcessed() {
        given(redisTemplate.hasKey("stock:restocked:item:10")).willReturn(true);

        restockService.restock(payload());

        verify(restockTransactionService, never()).increaseStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("락 내부 이중 체크 — 락 획득 후 이미 처리됨이면 복구 안 함")
    void restock_doubleCheckedInsideLock() throws InterruptedException {
        // 첫 체크 false → 락 진입 → 락 내부 재체크 true
        given(redisTemplate.hasKey("stock:restocked:item:10")).willReturn(false, true);
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).willReturn(true);
        lenient().when(lock.isHeldByCurrentThread()).thenReturn(true);

        restockService.restock(payload());

        verify(restockTransactionService, never()).increaseStock(anyLong(), anyInt());
        verify(lock).unlock();
    }
}
