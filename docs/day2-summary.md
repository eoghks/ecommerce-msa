# Day 2 작업 요약 — Frontend 개선 & Gateway 안정화

> 작업일: 2026-05-28  
> 브랜치: `feature/frontend-day2`

---

## 1. Gateway — 기동 순서 의존성 해결

### 문제
Gateway가 Auth-service보다 먼저 기동되면 `@PostConstruct` 시점에 JWKS 로드 실패 → `publicKey == null` → 이후 모든 JWT 검증 실패 → 로그인 화면으로 무한 리다이렉트.

### 해결

**`JwksClient.java`** — 빠른 복구 스케줄러 추가
- `fastRecovery()`: `@Scheduled(fixedDelay = 5000)` — `publicKey == null`인 동안 5초마다 JWKS 재시도
- Auth-service 기동 후 최대 5초 내 자동 복구, 정상 상태에서는 즉시 반환하므로 오버헤드 없음

**`JwtAuthenticationFilter.java`** — Optional 경로 fallback 추가
- `/api/v1/products`, `/api/v1/categories` 등 `OPTIONAL_AUTH_LIST` 경로는 토큰 검증 실패 시 401 반환 대신 익명 요청으로 통과
- `publicKey == null` / 서명 불일치 / 만료 / 파싱 오류 모두 동일하게 처리

---

## 2. Frontend — 로그인 UX 개선

### 문제
401 발생 시 axios 인터셉터가 토큰 삭제 후 `/login`으로 이동하는데, 아무 메시지도 없어 사용자가 왜 로그인 화면이 뜨는지 알 수 없음.

### 해결

**`axios.js`** — 401 리다이렉트 시 sessionStorage에 메시지 저장
```js
sessionStorage.setItem('loginMessage', '세션이 만료되었습니다. 다시 로그인해주세요.');
```

**`LoginPage.jsx`** — 마운트 시 sessionStorage 메시지 읽어 info-box로 표시 후 즉시 삭제

---

## 3. Frontend — 상품 목록 레이아웃 버그 수정

### 문제
페이지 1 → 2 → 3으로 이동할수록 상품 카드가 점점 작아지는 현상.

### 원인
`#root`가 `display: flex; flex-direction: column`인 상태에서 `<main>`에 `width` 없이 `margin: 0 auto`만 지정 → flex 컨텍스트에서 `align-items: stretch` 무력화 → content-intrinsic width로 수축.  
1페이지는 이미 로드된 이미지의 고유 크기가 내부 너비를 확보하지만, 이후 페이지는 이미지 미로드 상태에서 CSS Grid 컬럼이 0으로 수축 → 카드 축소.

### 해결
**`App.jsx`** — `<main>`에 `width: '100%'` 추가
```jsx
<main style={{ maxWidth: 1200, margin: '0 auto', padding: '24px 20px', width: '100%' }}>
```

---

## 4. Frontend — 상품 목록 UI 개선

### 그리드 변경
- 4열 → **5열** (`lg:grid-cols-5`)
- 카드 간격 축소 (`gap-3 sm:gap-4` → `gap-2 sm:gap-3`)
- 카드 텍스트 영역 패딩 축소 (`p-3.5` → `p-2.5`)
- 컨테이너 최대 너비 축소 (`maxWidth: 1600` → `maxWidth: 1200`)

### 페이지당 상품 수 선택
- 기본 **10개** (5열 × 2행)
- 결과 수 행 우측에 **10개 / 20개** 토글 버튼 추가
- 선택값을 URL searchParams(`?size=`)에 유지 → 새로고침/공유 시에도 설정 보존
- 사이즈 변경 시 자동으로 1페이지로 초기화

### 이미지 로딩 shimmer
- 이미지 로드 전 shimmer 스켈레톤 표시 (`@keyframes shimmer`)
- 로드 완료 후 fade-in 트랜지션 (`opacity: 0 → 1`)
- `aspect-square` + `absolute inset-0`으로 이미지 영역 1:1 비율 완전 고정

### 기타
- `scrollbar-gutter: stable` 적용 — 스크롤바 유무에 따른 레이아웃 시프트 방지

---

## 변경 파일 목록

| 파일 | 변경 내용 |
|------|-----------|
| `gateway/.../JwksClient.java` | fastRecovery 스케줄러 추가 |
| `gateway/.../JwtAuthenticationFilter.java` | Optional 경로 익명 통과 fallback |
| `frontend/src/api/axios.js` | 401 리다이렉트 시 sessionStorage 메시지 |
| `frontend/src/pages/auth/LoginPage.jsx` | sessionStorage 메시지 표시 |
| `frontend/src/App.jsx` | `<main>` width 100% + maxWidth 1200 |
| `frontend/src/index.css` | scrollbar-gutter, shimmer keyframes |
| `frontend/src/pages/product/ProductListPage.jsx` | 5열 그리드, 페이지당 수 선택, shimmer |
