package com.ecommerce.product.exception;

/** 판매중이 아닌(판매금지) 상품을 찜 추가하려 할 때 */
public class ProductNotOnSaleException extends RuntimeException {

    public ProductNotOnSaleException(Long productId) {
        super("판매중인 상품이 아니어서 찜할 수 없습니다. productId=" + productId);
    }
}
