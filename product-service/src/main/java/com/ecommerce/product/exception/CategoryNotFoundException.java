package com.ecommerce.product.exception;

public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(Long id) {
        super("카테고리를 찾을 수 없습니다. id=" + id);
    }
}
