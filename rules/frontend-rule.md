# 프론트엔드 규칙 (React)

## 기술 스택
| 분류 | 선택 |
|------|------|
| 언어 | TypeScript |
| 빌드 | Vite |
| 라우팅 | React Router v6 |
| HTTP | Axios |
| 서버 상태 | TanStack Query |
| 클라이언트 상태 | **Zustand** (Context API 사용 안 함) |
| 폼 | React Hook Form + Zod |
| 스타일 | Tailwind CSS |
| 테스트 | Vitest + RTL + MSW |
| 린트 | ESLint + Prettier |

## 프로젝트 구조 (Feature-Sliced)
```
frontend/src/
├── app/          # 라우터, 전역 Provider
├── features/     # 도메인별 (auth, product, order, cart)
│   └── <domain>/
│       ├── api/
│       ├── components/
│       ├── hooks/
│       ├── store/
│       └── types.ts
├── shared/       # 공통 컴포넌트, 유틸, 타입
├── pages/        # 라우트 단위 (features 조합)
└── styles/
```
- features 끼리 직접 import 금지 (순환 의존 방지) — `shared/` 또는 이벤트로 통신
- barrel export(`index.ts`)는 features 경계에서만, 내부엔 지양

## 컴포넌트
- 파일명: PascalCase (`ProductCard.tsx`)
- 함수형만, 컴포넌트당 파일 1개
- 200줄 또는 책임 2개 이상이면 분리
- Props 4개 초과 → 객체로 묶기, 인터페이스로 명시

## 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `ProductList` |
| 커스텀 훅 | `use<기능>` | `useAuth` |
| 이벤트 핸들러 | `handle<이벤트>` | `handleSubmit` |
| API 함수 | `<동사><도메인>` | `fetchProducts` |
| 타입 | PascalCase, 접미사 금지 | `Product` |

## 인증 / 토큰 처리 (보안 핵심)
- **Access Token**: 메모리(Zustand) 저장 — `localStorage` 금지 (XSS 노출)
- **Refresh Token**: `httpOnly + Secure + SameSite=Strict` 쿠키 — JS 접근 불가
- 새로고침 시 Access 휘발 → 앱 부팅 시 `POST /auth/refresh` 로 메모리 복구
- **CSRF 정책**: Refresh 가 쿠키 기반이므로
  - 백엔드: `/auth/refresh` 엔드포인트만 Origin 헤더 검증 ([security-rule.md](../rules/security-rule.md))
  - 프론트: 명시적 Refresh 호출은 Axios 인터셉터 내부에서만, 외부 노출 안 함

## API 호출 규칙
- Axios 인스턴스 `shared/api/client.ts` 중앙 관리
- 인터셉터:
  - Request: Access Token 헤더, `X-Request-ID` 부여
  - Response: ProblemDetail 파싱 → `AppError` 변환
  - 401: **Refresh 큐잉** — 동시 401 시 단 한 번만 `/auth/refresh`, 나머지는 큐 대기 후 재시도
  - Refresh 실패 (만료): 로그아웃 / Refresh 실패 (다른 에러): 토스트
- 사전 갱신(silent refresh): 토큰 만료 1분 전 자동 재발급 (선택)
- 호출은 **반드시 TanStack Query** — `useEffect` fetch 금지

## ProblemDetail 처리
백엔드 응답:
```json
{ "type": "...", "title": "...", "status": 400, "detail": "...", "errors": [{"field":"email","message":"..."}] }
```
매핑:
- `errors[]` → React Hook Form `setError(field, { message })`
- `detail` → Toast
- `status` → 라우팅 분기 (401 로그인, 403 접근 불가)

## 상태 관리
- 서버 상태: TanStack Query 단일 출처 — Zustand 중복 저장 금지
- 전역 클라이언트 상태: Zustand
- 로컬 UI: `useState`
- TanStack Query 정책:
  - `staleTime`: 상품 목록 1분 / 사용자 정보 5분
  - `gcTime`: 5분
  - `refetchOnWindowFocus`: 결제·주문만 활성

## 폼
- React Hook Form + Zod
- ProblemDetail `errors[]` → 필드 에러 매핑
- `isSubmitting` 으로 더블 클릭 방지

## 에러 처리
- 전역 Error Boundary (라우트 레벨)
- API 에러: TanStack Query `onError`/`error`
- Suspense + Error Boundary 조합

## 낙관적 업데이트
- 장바구니 추가/수량 변경 등 즉시 반영
- `mutate` `onMutate` 캐시 갱신, 실패 시 롤백
- 백엔드 멱등키(`Idempotency-Key`) 로 중복 방지

## 성능
- `React.memo` / `useMemo` / `useCallback`: 측정된 비용에만
- 코드 분할: 페이지 단위 `React.lazy` + `Suspense`
- 이미지: lazy load + WebP, hero 는 `fetchpriority="high"`
- 번들 사이즈 예산: 200KB gzip
- Web Vitals (LCP/INP/CLS) 측정·리포팅

## 접근성
- semantic HTML, `label` 연결, 이미지 `alt`
- 키보드 네비게이션, 모달 focus trap
- `aria-*` 적절히, 색 대비 WCAG AA
- `prefers-reduced-motion` 존중

## 보안
- `dangerouslySetInnerHTML` 금지 (필수 시 DOMPurify)
- 외부 링크 `rel="noopener noreferrer"`
- CSP 는 정적 호스팅 측 (Cloudflare Pages, Vercel)

## 국제화 / 통화
- 통화: `Intl.NumberFormat('ko-KR', { style: 'currency', currency: 'KRW' })`
- 날짜: `Intl.DateTimeFormat` 또는 `date-fns` (timezone 명시)

## 테스트
- 단일 출처: [testing-rule.md](testing-rule.md)
- 컴포넌트: RTL + Vitest, 사용자 관점 쿼리
- API 모킹: MSW

## 환경변수
- `.env`, prefix `VITE_`
- `.env` 커밋 금지

## 기타
- 절대경로 import (`@/features/auth`)
- Storybook (선택, 면접 어필)
- Lighthouse CI (선택)
