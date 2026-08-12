package com.ecommerce.order;

import com.ecommerce.order.support.ServiceTimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.ecommerce")
public class OrderServiceApplication {

    public static void main(String[] args) {
        // M-3: 주문 시각 기록·통계 집계가 JVM 기본 타임존에 좌우되므로 기동 최초에 KST 로 고정한다.
        ServiceTimeZone.applyDefault();
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
