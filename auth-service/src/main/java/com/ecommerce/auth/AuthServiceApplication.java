package com.ecommerce.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

// scanBasePackages: common 모듈의 GlobalExceptionHandler, MdcLoggingFilter 자동 등록
@SpringBootApplication(scanBasePackages = "com.ecommerce")
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
