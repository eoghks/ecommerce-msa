# 테스트 규칙

## 원칙
- 코드 작성과 동시에 테스트 코드 작성 (별도 요청 없이)
- 전체 통과 시에만 완료 보고 — 실패 시 수정 후 재실행
- 보고 형식: `통과 N건 / 실패 0건`

## 테스트 범위
| 레이어 | 도구 | 범위 |
|--------|------|------|
| Service | JUnit 5 + Mockito | 비즈니스 로직 단위 테스트 |
| Repository | `@DataJpaTest` | JPA 쿼리 검증 |
| Controller | `@WebMvcTest` + MockMvc | API 요청/응답 검증 |
| 통합 | `@SpringBootTest` | 핵심 시나리오 E2E |

## 테스트 클래스 네이밍
```
<대상클래스>Test.java
예: AuthServiceTest, ProductRepositoryTest, OrderControllerTest
```

## 테스트 메서드 네이밍
```
<메서드명>_<상황>_<기대결과>
예:
login_정상입력_JWT토큰반환()
login_존재하지않는이메일_예외발생()
createOrder_재고부족_StockInsufficientException발생()
```

## Given-When-Then 패턴 준수
```java
@Test
void login_정상입력_JWT토큰반환() {
    // given
    LoginRequest request = ...;

    // when
    LoginResponse response = authService.login(request);

    // then
    assertThat(response.accessToken()).isNotNull();
}
```

## 기타
- 외부 의존성(Redis, DB)은 Mockito로 Mock 처리 (단위 테스트)
- `@DataJpaTest` 는 H2 인메모리 DB 사용
- 테스트 픽스처는 별도 `fixture/` 패키지에 관리
