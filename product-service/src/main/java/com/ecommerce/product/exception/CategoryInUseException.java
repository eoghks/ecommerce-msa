package com.ecommerce.product.exception;

public class CategoryInUseException extends RuntimeException {

    public CategoryInUseException(Long id, long productCount) {
        super("해당 카테고리를 참조하는 상품이 존재하여 삭제할 수 없습니다. id="
                + id + ", 상품 수=" + productCount);
    }
}
