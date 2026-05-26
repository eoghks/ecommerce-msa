# UI 디자인 시스템 — ecommerce-msa Frontend

> Claude Design 기반으로 생성된 디자인 가이드라인  
> 생성일: 2025-05-26

---

## 1. 디자인 컨셉

**키워드**: 신뢰감 · 미니멀 · 모던  
**레퍼런스**: Linear, Vercel, Stripe 스타일의 클린 SaaS UI

---

## 2. 컬러 팔레트

| 역할 | 이름 | HEX | 용도 |
|------|------|-----|------|
| Primary | Indigo 600 | `#4f46e5` | 버튼, 링크, 포커스 링 |
| Primary Dark | Indigo 700 | `#4338ca` | 버튼 호버 |
| Primary Light | Indigo 50 | `#eef2ff` | 배경 강조 |
| Neutral 900 | Gray 900 | `#111827` | 제목 텍스트 |
| Neutral 600 | Gray 600 | `#4b5563` | 본문 텍스트 |
| Neutral 400 | Gray 400 | `#9ca3af` | 플레이스홀더 |
| Neutral 200 | Gray 200 | `#e5e7eb` | 테두리, 구분선 |
| Neutral 50  | Gray 50  | `#f9fafb` | 페이지 배경 |
| Error | Red 500 | `#ef4444` | 오류 메시지 |
| Success | Green 500 | `#22c55e` | 성공 메시지 |
| White | — | `#ffffff` | 카드 배경 |

---

## 3. 타이포그래피

| 구분 | 크기 | 굵기 | 용도 |
|------|------|------|------|
| Page Title | 28px | 700 | 페이지 대제목 |
| Section Title | 20px | 600 | 섹션 제목 |
| Body | 14px | 400 | 일반 본문 |
| Label | 13px | 500 | 폼 레이블 |
| Caption | 12px | 400 | 보조 설명 |

폰트: 시스템 폰트 스택 (`-apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif`)

---

## 4. 간격 시스템 (8px 기반)

| 토큰 | 값 | 용도 |
|------|-----|------|
| xs | 4px | 아이콘-텍스트 간격 |
| sm | 8px | 요소 내부 여백 |
| md | 16px | 컴포넌트 간격 |
| lg | 24px | 섹션 간격 |
| xl | 32px | 카드 패딩 |
| 2xl | 48px | 페이지 여백 |

---

## 5. 컴포넌트 스펙

### 5-1. 페이지 레이아웃 (인증 페이지)
- 배경: `#f9fafb` (Gray 50) + 미묘한 그리드 패턴
- 카드: 흰색, `border-radius: 16px`, `box-shadow: 0 4px 24px rgba(0,0,0,0.08)`
- 카드 너비: 최대 420px, 패딩 40px
- 상단 로고/아이콘 영역: 아이콘 + 브랜드명

### 5-2. 입력 필드 (Input)
- 높이: 44px
- 테두리: `1px solid #e5e7eb`, radius `10px`
- 포커스: `border-color: #4f46e5`, `box-shadow: 0 0 0 3px rgba(79,70,229,0.15)`
- 플레이스홀더: `#9ca3af`
- 아이콘: 좌측 16px 패딩 + SVG 아이콘

### 5-3. 기본 버튼 (Primary Button)
- 배경: `#4f46e5` → 호버: `#4338ca`
- 텍스트: 흰색, 15px, 600
- 높이: 44px, radius `10px`
- 전환: `transition: all 0.15s ease`
- 비활성: `opacity: 0.6`, 커서 금지

### 5-4. 소셜 로그인 구분선
- `─── 또는 ───` 스타일 구분선
- 색상: `#e5e7eb`

### 5-5. 링크
- 색상: `#4f46e5`, 호버 시 underline
- 전환: `0.1s`

---

## 6. 인터랙션 원칙

- 포커스 시 `box-shadow` 링 표시 (접근성)
- 버튼 로딩 상태: 텍스트 → "처리 중..." + 비활성
- 에러 메시지: 입력 아래 즉시 표시, 빨간색
- 입력 변경 시 에러 자동 초기화

---

## 7. 적용 페이지 목록

| 페이지 | 파일 | 적용일 |
|--------|------|--------|
| 로그인 | `LoginPage.jsx` | 2025-05-26 |
| 회원가입 | `RegisterPage.jsx` | 2025-05-26 |
| 상품 목록 | `ProductListPage.jsx` | Day2 예정 |
| 상품 상세 | `ProductDetailPage.jsx` | Day2 예정 |
| 장바구니 | `CartPage.jsx` | Day3 예정 |
| 주문 | `OrderPage.jsx` | Day3 예정 |
