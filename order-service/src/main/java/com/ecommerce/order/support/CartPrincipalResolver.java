package com.ecommerce.order.support;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.Arrays;
import java.util.Optional;

/**
 * CartPrincipal 파라미터 자동 바인딩.
 * X-User-Id 헤더(로그인) 또는 guestId 쿠키(비로그인)를 추출해 CartPrincipal 생성.
 */
@Component
public class CartPrincipalResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return CartPrincipal.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = (HttpServletRequest) webRequest.getNativeRequest();

        String userIdHeader = request.getHeader("X-User-Id");
        Long userId = userIdHeader != null ? Long.parseLong(userIdHeader) : null;

        String guestId = Arrays.stream(Optional.ofNullable(request.getCookies()).orElse(new Cookie[0]))
                .filter(c -> "guestId".equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);

        return new CartPrincipal(userId, guestId);
    }
}
