# 코딩 규칙

## 공통
- Java 21, `jakarta.*` 사용 (`javax.*` 금지)
- Lombok 적극 사용
- 한국어 주석 허용
- 메서드 30줄 이내
- null 대신 `Optional` / 빈 컬렉션
- 매직 넘버/문자열은 상수
- 함수 인자로 `Map` 금지 — 명시적 DTO 사용

## RESTful 설계
- HTTP Method 의미대로: `GET`(조회) `POST`(생성) `PUT`(전체수정) `PATCH`(부분수정) `DELETE`(삭제)
- URI는 리소스 명사 (동사 금지)
- HTTP Status Code 정확히 사용:
  - `200` 조회/수정 / `201` 생성(+Location) / `204` 삭제
  - `400` 검증실패 / `401` 인증 / `403` 권한 / `404` 없음
  - `409` 중복·재고부족 / `422` 비즈니스규칙 위반 / `500` 서버오류

## 레이어 책임
- **Controller**: 입력 바인딩 + 검증(`@Valid`)만, 응답은 `ResponseEntity<DTO>` 직접 반환 (래퍼 금지)
- **Service**: 비즈니스 로직, `@Transactional` 관리 (조회는 `readOnly = true`)
- **Repository**: 단순 쿼리는 메서드 네이밍, 복잡 쿼리는 QueryDSL (JPQL·네이티브 지양)

## 예외 처리
- 실패는 커스텀 예외 throw (Controller try/catch 금지)
- 전역 처리: `@RestControllerAdvice` + `ProblemDetail` (RFC 7807)
- 예외 메시지는 사용자에게 노출되지 않게 추상화

## 파일 생성
- `.java` `.jsx` `.js` `.xml` 생성 즉시 `git add` 실행
