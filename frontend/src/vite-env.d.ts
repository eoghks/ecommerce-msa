/// <reference types="vite/client" />

/** 프론트에서 쓰는 환경변수 — 값은 .env(.example) 참조. 시크릿은 절대 두지 않는다 */
interface ImportMetaEnv {
  /** 게이트웨이 주소. 빈 값이면 Vite 프록시가 /api 를 포워딩 */
  readonly VITE_API_BASE_URL?: string;
  /** V1.1-6: 토스페이먼츠 결제위젯 클라이언트 키(공개값) */
  readonly VITE_TOSS_CLIENT_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
