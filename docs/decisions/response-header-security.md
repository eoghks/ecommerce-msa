# 응답 헤더 보안 (구현 보류 — 학습 메모)

## 상태
- **현재**: 미구현 (Spring Security 기본값만 적용)
- **사유**: 토이프로젝트 단계, 핵심 기능 우선
- **재검토 시점**: Week 6 마무리 또는 운영 시뮬레이션 시

---

## 왜 필요한가 (Defense in Depth)

서버 측 검증이 1차 방어라면, 응답 헤더는 **브라우저에 보안 동작을 지시**하는 2차 방어.

| 헤더 | 막는 공격 | 동작 |
|------|----------|------|
| `Content-Security-Policy` | XSS | 실행 가능한 스크립트 출처 화이트리스트 |
| `X-Content-Type-Options: nosniff` | MIME 스니핑 | 파일 확장자 위장 통한 스크립트 실행 차단 |
| `X-Frame-Options: DENY` | Clickjacking | iframe 임베딩 자체 차단 |
| `Strict-Transport-Security` | MITM | HTTPS 강제, HTTP 접근 자동 차단 |
| `Referrer-Policy` | URL 정보 유출 | 외부 이동 시 referrer 노출 제한 |
| `Permissions-Policy` | 권한 남용 | 카메라·마이크·위치 등 차단 |

---

## 구현 — Spring Cloud Gateway

### 1. SecurityFilterChain 설정
```java
@Configuration
@EnableWebFluxSecurity
public class GatewaySecurityConfig {

    @Bean
    public SecurityWebFilterChain securityFilterChain(ServerHttpSecurity http) {
        return http
            .csrf(ServerHttpSecurity.CsrfSpec::disable)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives(
                    "default-src 'self'; " +
                    "script-src 'self'; " +
                    "style-src 'self' 'unsafe-inline'; " +
                    "img-src 'self' data:; " +
                    "connect-src 'self'"
                ))
                .frameOptions(frame -> frame.mode(Mode.DENY))
                .contentTypeOptions(Customizer.withDefaults())
                .hsts(hsts -> hsts
                    .includeSubdomains(true)
                    .maxAge(Duration.ofDays(365))
                )
                .referrerPolicy(rp -> rp.policy(NO_REFERRER_WHEN_DOWNGRADE))
                .permissionsPolicy(pp -> pp.policy(
                    "camera=(), microphone=(), geolocation=()"
                ))
            )
            .build();
    }
}
```

### 2. CSP 정책 가이드

CSP 가 가장 중요하고 가장 까다로움. 잘못 설정하면 정상 페이지도 깨짐.

```
default-src 'self'                      # 기본은 같은 도메인만
script-src 'self'                       # JS 는 같은 도메인만 (인라인 금지)
style-src 'self' 'unsafe-inline'        # CSS 인라인 허용 (React/Tailwind 호환)
img-src 'self' data:                    # 이미지: 같은 도메인 + base64 data URI
connect-src 'self'                      # XHR/fetch: 같은 도메인만
```

**개발 시작 시 점진 적용**:
1. `Content-Security-Policy-Report-Only` 헤더로 먼저 시도 (위반 보고만, 차단 안 함)
2. 콘솔에서 위반 항목 확인 후 정책 조정
3. 안정화 후 `Content-Security-Policy` 로 전환

---

## 검증 방법

### 브라우저 개발자도구
- Network 탭 → 응답 헤더 확인
- Console 탭 → CSP 위반 시 에러 표시

### 외부 검증 도구
- **[securityheaders.com](https://securityheaders.com)** — URL 입력하면 등급(A~F) 평가
- **Mozilla Observatory** — 종합 보안 점수

---

## 면접 답변 시나리오

> Q: 보안 헤더 설정은 했나요?
> A: Spring Security 기본값(X-Frame-Options 등)은 적용돼있고, CSP·HSTS·Permissions-Policy 등 추가 헤더는 우선순위 낮춰 미구현했습니다. Gateway `SecurityFilterChain` 에 일괄 적용하는 코드까지 설계는 마쳤고, securityheaders.com A 등급 목표로 정책 정리해뒀습니다.

---

## 참고 자료
- [OWASP Secure Headers Project](https://owasp.org/www-project-secure-headers/)
- [MDN — Content Security Policy](https://developer.mozilla.org/en-US/docs/Web/HTTP/CSP)
- [Mozilla Observatory](https://observatory.mozilla.org)
