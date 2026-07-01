# MD-05: 서비스 내부망 격리 미적용 (docker-compose 서비스 컨테이너 미정의)

## 현재 구현

docker-compose에 인프라(postgres, redis, kafka)만 정의되어 있고 애플리케이션 서비스(gateway, auth-service, product-service)는 미정의.

로컬에서 `./gradlew bootRun`으로 직접 실행 중.

X-User-Role 헤더는 Gateway가 주입하지만, product-service/auth-service 포트가 외부에 노출되면 헤더 위조로 우회 가능.

## 문제점

```
악의적 요청
POST http://localhost:8082/api/v1/products
X-User-Role: ADMIN   ← 직접 위조 → ADMIN API 접근 가능
```

## Week 6에서 할 것 (필수)

docker-compose에 서비스 컨테이너 추가 시 **ports 미노출**로 외부 접근 차단.

```yaml
# gateway만 외부 포트 노출
gateway:
  ports:
    - "8080:8080"
  networks:
    - ecommerce-net

# auth-service, product-service — ports 없음
auth-service:
  networks:
    - ecommerce-net  # 내부 네트워크만

product-service:
  networks:
    - ecommerce-net  # 내부 네트워크만
```

외부에서 8081, 8082 직접 접근 불가 → X-User-Role 헤더 위조 불가.

## 운영 수준 해결책

| 방법 | 설명 | 적용 시점 |
|------|------|----------|
| 내부망 격리 | Docker/K8s 네트워크로 외부 접근 차단 | Week 6 |
| 서비스 메시 (Istio) | mTLS 자동 적용, 인증서 없는 요청 거부 | 운영 |
| 내부 JWT | Gateway가 서비스 전용 내부 토큰 발급 | 운영 |

## 포트폴리오 선택 이유

Week 6 docker-compose 서비스 컨테이너 정의 시 자연스럽게 해결 예정. 로컬 개발 단계에서 인프라 격리 구성 선행은 오버헤드.

## 면접 답변 포인트

> "X-User-Role 헤더 위조는 어떻게 막나요?"
> → 1차: Docker 네트워크로 서비스 포트를 외부에 노출하지 않아 Gateway를 통해서만 접근 가능하도록 합니다. 운영 환경에서는 Istio 서비스 메시로 mTLS를 적용해 인증서 없는 서비스 간 통신을 거부합니다.
