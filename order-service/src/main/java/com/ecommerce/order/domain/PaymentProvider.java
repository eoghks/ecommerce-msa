package com.ecommerce.order.domain;

/** 결제 대행사 (payment-foundation §11-1) */
public enum PaymentProvider {

    /** 토스페이먼츠 샌드박스 */
    TOSS,

    /** PG 미사용 — payable=0 전액 할인 결제(§11-3) */
    NONE
}
