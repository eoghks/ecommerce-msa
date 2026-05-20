package com.ecommerce.product.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka 에러 처리 설정.
 * - 재시도: 3회 (1초 간격)
 * - 최종 실패: Dead Letter Topic (.DLT 접미사) 으로 이동
 */
@Slf4j
@EnableKafka
@Configuration
public class KafkaConfig {

    /**
     * DefaultErrorHandler: 재시도 후 실패 시 DLT로 전송.
     * FixedBackOff(1000, 2) → 1초 간격으로 최대 2회 재시도 (총 3회 시도)
     */
    @Bean
    public DefaultErrorHandler errorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (record, ex) -> {
                    log.error("Kafka 메시지 처리 최종 실패 → DLT 전송. topic={}, key={}, error={}",
                            record.topic(), record.key(), ex.getMessage());
                    // DLT 토픽명: 원본토픽.DLT (예: order.created.DLT)
                    return new org.apache.kafka.common.TopicPartition(
                            record.topic() + ".DLT", record.partition());
                });

        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
    }
}
