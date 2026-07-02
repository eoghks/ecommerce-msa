package com.ecommerce.product.exception;

/**
 * SELLER 권한 요청인데 인증 주체(X-User-Id)가 없을 때 발생.
 * 게이트웨이 인증 성공 시 X-User-Id가 주입되므로, null이면 비정상 요청으로 간주해 거부.
 */
public class MissingSellerIdException extends RuntimeException {
    public MissingSellerIdException() {
        super("판매자 인증 정보가 없습니다. 다시 로그인해주세요.");
    }
}
