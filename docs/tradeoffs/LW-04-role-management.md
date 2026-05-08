# LW-04 · Role 관리 API 미구현

**대상:** `auth-service`

## 현재 구현

회원가입 시 `Role.USER`로 고정. ADMIN 계정은 `DataInitializer`로 기동 시 시드 생성.

```java
// AuthService.signup()
.role(Role.USER)  // 항상 USER로 고정
```

## 문제점

ADMIN이 특정 USER를 ADMIN으로 승격하거나 권한을 변경하는 API가 없음.
운영 환경에서는 DB 직접 수정 없이 역할 변경 불가.

## 운영 수준 해결책

```
PATCH /api/v1/users/{id}/role
Authorization: Bearer <ADMIN 토큰>
Body: { "role": "ADMIN" }
```

```java
// ADMIN 권한 체크 후 role 변경
@PreAuthorize("hasRole('ADMIN')")
public void updateRole(Long userId, Role role) {
    User user = userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("사용자 없음"));
    user.updateRole(role);
}
```

## 포트폴리오 선택 이유

Week 2 Auth Service 범위 초과. 관리자 대시보드(React) 구현 시점에
사용자 관리 화면과 함께 자연스럽게 추가 예정.

## 면접 답변 포인트

> "현재는 DataInitializer로 초기 ADMIN 시드 계정을 생성하고, 역할 변경 API는
> 관리자 대시보드 구현 시점에 PATCH /users/{id}/role 엔드포인트로 추가할 계획입니다.
> ADMIN 권한 체크는 @PreAuthorize 또는 Gateway SecurityConfig에서 경로별로 제한합니다."
