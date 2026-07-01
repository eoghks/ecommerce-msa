package com.ecommerce.order.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * CartPrincipal 파라미터 자동 바인딩.
 * X-User-Id 헤더(로그인) 또는 guestId 쿠키(비로그인)를 추출해 CartPrincipal 생성.
 */
@Slf4j
@Component
public class CartPrincipalResolver implements HandlerMethodArgumentResolver {

    // CR-02: UUID v4 형식만 허용 — Redis Key Injection 방지
    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$");

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CartPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        // CR-03: NumberFormatException 방어 — 파싱 실패 시 비로그인으로 처리
        Long userId = null;
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            try {
                userId = Long.parseLong(userIdHeader);
            } catch (NumberFormatException e) {
                log.warn("X-User-Id 헤더 파싱 실패, 비로그인으로 처리: value={}", userIdHeader);
            }
        }

        // CR-02: UUID v4 형식 검증 — 불일치 시 null 처리
        String guestId = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "guestId".equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> {
                    boolean valid = UUID_PATTERN.matcher(v).matches();
                    if (!valid) log.warn("guestId 쿠키 형식 불일치, 무시: value={}", v);
                    return valid;
                })
                .findFirst()
                .orElse(null);

        return new CartPrincipal(userId, guestId);
    }
}
