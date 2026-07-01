# CR-01 · RSA 키 페어 인메모리 생성

**대상:** `auth-service` — `JwtProvider.java`

## 현재 구현

```java
@PostConstruct
public void init() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    KeyPair pair = gen.generateKeyPair();  // 매 기동 시 새 키 생성
}
```

## 문제점

- 서비스 **재시작 시** 기존 발급된 모든 Access Token 즉시 무효화
- **스케일 아웃** 시 인스턴스마다 다른 키 → 다른 인스턴스가 발급한 토큰 검증 불가

## 운영 수준 해결책

```yaml
# application.yml
jwt:
  private-key-pem: ${JWT_PRIVATE_KEY}   # K8s Secret 또는 Vault 주입
  public-key-pem:  ${JWT_PUBLIC_KEY}
```
```java
@PostConstruct
public void init() throws Exception {
    byte[] keyBytes = Base64.getDecoder().decode(
        privateKeyPem.replaceAll("-----.*-----|\n", ""));
    PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
    this.privateKey = (RSAPrivateKey) KeyFactory.getInstance("RSA").generatePrivate(spec);
}
```

## 포트폴리오 선택 이유

단일 인스턴스 로컬 환경 → 재시작 빈도 낮고 스케일 아웃 없음.
키 관리 인프라(Vault, K8s Secret) 없이 PEM 주입 시 `.env` / `application.yml`에
평문 키가 노출되어 보안상 더 나쁠 수 있음.

## 면접 답변 포인트

> "현재는 인메모리 생성 방식인데, 운영 환경에서는 AWS Secrets Manager나 Kubernetes Secret으로
> PEM을 외부 주입해야 합니다. 특히 스케일 아웃 환경에서는 모든 인스턴스가 동일한 키를 공유해야
> Gateway가 어떤 인스턴스가 발급한 토큰도 검증할 수 있습니다."
