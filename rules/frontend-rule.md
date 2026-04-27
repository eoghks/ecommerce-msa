# 프론트엔드 규칙 (React)

## 프로젝트 구조
```
frontend/src/
├── api/          # Axios 호출 함수
├── components/   # 공통 컴포넌트
├── pages/        # 라우트별 페이지 컴포넌트
├── hooks/        # 커스텀 훅
├── store/        # 상태 관리 (Context 또는 Zustand)
├── styles/       # 전역 스타일
└── utils/        # 유틸 함수
```

## 컴포넌트 규칙
- 컴포넌트 파일명: PascalCase (예: `ProductCard.jsx`)
- 컴포넌트당 파일 1개
- 200줄 초과 시 하위 컴포넌트로 분리
- Props는 구조분해할당으로 받기

## 네이밍
| 유형 | 규칙 | 예시 |
|------|------|------|
| 컴포넌트 | PascalCase | `ProductList`, `OrderCard` |
| 커스텀 훅 | `use<기능>` | `useAuth`, `useCart` |
| 이벤트 핸들러 | `handle<이벤트>` | `handleSubmit`, `handleDelete` |
| API 함수 | `<동사><도메인>` | `fetchProducts`, `createOrder` |

## API 호출 규칙
- Axios 인스턴스를 `api/` 에서 중앙 관리
- Access Token은 Axios 인터셉터로 자동 헤더 삽입
- 401 응답 시 Refresh Token으로 재발급 후 재요청

## 상태 관리
- 서버 상태: Axios + `useState`/`useEffect`
- 전역 클라이언트 상태 (로그인 정보 등): Context API

## 기타
- 환경변수는 `.env` 파일로 관리 (`REACT_APP_API_URL` 등)
- `.env` 파일 커밋 금지
- 절대경로 import 설정 (`jsconfig.json`)
