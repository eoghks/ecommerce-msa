# 프론트엔드 규칙 (React)

## 기술 스택
| 분류 | 선택 | 이유 |
|------|------|------|
| 언어 | **TypeScript** | 타입 안정성, 면접 표준 |
| 빌드 도구 | **Vite** | 빠른 HMR, CRA 는 deprecated |
| 라우팅 | **React Router v6** | 표준 |
| HTTP | **Axios** | 인터셉터, 표준 |
| 서버 상태 | **TanStack Query (React Query)** | 캐싱·재시도·동기화 자동 — `useEffect` 직접 사용 지양 |
| 클라이언트 상태 | **Zustand** 또는 **Context API** | Zustand 추천 (간결) |
| 폼 | **React Hook Form + Zod** | 검증·타입 추론 표준 조합 |
| 스타일 | **Tailwind CSS** | 빠른 개발, 일관성 |
| 테스트 | **Vitest + React Testing Library** | Vite 친화적 |
| 린트 | **ESLint + Prettier** | 표준 |

## 프로젝트 구조
```
frontend/src/
├── api/          # Axios 인스턴스 + 도메인별 API 함수
├── components/   # 공통/재사용 컴포넌트
├── pages/        # 라우트 단위 페이지
├── hooks/        # 커스텀 훅
├── store/        # Zustand 스토어
├── types/        # 공용 타입 정의
├── utils/        # 유틸 함수
└── styles/       # 전역 스타일 (tailwind.config 포함)
```

## 컴포넌트 규칙
- 파일명: PascalCase (`ProductCard.tsx`)
- 컴포넌트당 파일 1개, 200줄 초과 시 분리
- Props 는 구조분해할당, 타입은 인터페이스로 명시
- 함수형 컴포넌트만 사용 (class 컴포넌트 금지)
- Props 가 4개 초과면 객체로 묶기 검토

## 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `ProductList` |
| 커스텀 훅 | `use<기능>` | `useAuth`, `useCart` |
| 이벤트 핸들러 | `handle<이벤트>` | `handleSubmit` |
| API 함수 | `<동사><도메인>` | `fetchProducts`, `createOrder` |
| 타입/인터페이스 | PascalCase, suffix `Type` 금지 | `Product`, `LoginRequest` |

## API 호출 규칙
- Axios 인스턴스를 `api/client.ts` 에서 중앙 관리
- 인터셉터로 처리:
  - Request: Access Token 자동 헤더 삽입, `X-Request-ID` 부여
  - Response: 401 → Refresh Token 재발급 후 재요청, 실패 시 로그아웃
  - 에러: `ProblemDetail` 응답 파싱 후 일관된 에러 객체로 변환
- API 호출은 **반드시 TanStack Query** 로 감싸서 사용 (`useQuery`, `useMutation`)
- `useEffect` 안에서 fetch 직접 호출 금지

## 상태 관리
- **서버 상태** (상품 목록, 주문 등): TanStack Query
- **전역 클라이언트 상태** (로그인 정보, 장바구니 UI): Zustand
- **로컬 UI 상태** (모달 열림 등): `useState`
- 서버에 있는 데이터를 Zustand 에 복사 저장 금지 — TanStack Query 가 단일 출처

## 폼 처리
- React Hook Form `useForm` + Zod 스키마
- 백엔드 ProblemDetail 의 `errors[]` → 필드 에러로 매핑
- 제출 중 더블클릭 방지 (`isSubmitting` 비활성)

## 에러 처리
- 전역 Error Boundary 로 렌더링 에러 차단
- API 에러는 TanStack Query `onError` 또는 `error` 상태로 처리
- 사용자 친화 메시지 (Toast) 표시, 상세는 콘솔에만

## 로딩 / 스켈레톤
- TanStack Query `isPending` 으로 로딩 표시
- 스켈레톤 UI 우선, 스피너는 빠른 액션에만

## 접근성 (a11y)
- semantic HTML 사용 (`button`, `nav`, `main`)
- 이미지 `alt` 필수
- 폼 필드는 `label` 연결
- 키보드 네비게이션 가능

## 테스트
- 컴포넌트: React Testing Library + Vitest
- 사용자 관점 쿼리 우선 (`getByRole`, `getByLabelText`)
- 구현 디테일(`getByTestId`) 의존 최소화
- API 모킹: MSW (Mock Service Worker)

## 환경변수
- `.env` 파일, prefix `VITE_` (예: `VITE_API_BASE_URL`)
- `.env` 커밋 금지

## 기타
- 절대경로 import (`@/components/ProductCard`)
- 코드 분할: 페이지 단위 `React.lazy` + `Suspense`
- 이미지 최적화: lazy load, WebP 우선
