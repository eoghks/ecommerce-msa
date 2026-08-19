/**
 * 토스페이먼츠 결제위젯 SDK 로더 (V1.1-6, payment-foundation §11-1).
 * index.html 에 고정 script 를 넣지 않고 결제 화면에서만 동적으로 불러온다
 * — 결제와 무관한 페이지에서 외부 스크립트를 받지 않아 초기 로딩·추적 노출이 줄어든다.
 * 클라이언트 키는 브라우저에 노출되는 공개값이며, 시크릿키는 서버에만 둔다.
 */

const SDK_URL = 'https://js.tosspayments.com/v2/standard';

/** 결제 금액 — 통화와 금액을 함께 넘긴다(SDK v2 규약) */
export interface TossAmount {
  currency: 'KRW';
  value: number;
}

/** 위젯 렌더 대상 — selector 는 이미 DOM 에 있는 엘리먼트여야 한다 */
export interface TossRenderOptions {
  selector: string;
  variantKey?: string;
}

/** 결제 요청 — 성공·실패 시 SDK 가 지정 URL 로 리다이렉트한다 */
export interface TossPaymentRequest {
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
  customerName?: string;
}

export interface TossPaymentWidgets {
  setAmount(amount: TossAmount): Promise<void>;
  renderPaymentMethods(options: TossRenderOptions): Promise<unknown>;
  renderAgreement(options: TossRenderOptions): Promise<unknown>;
  requestPayment(request: TossPaymentRequest): Promise<void>;
}

export interface TossPaymentsInstance {
  widgets(options: { customerKey: string }): TossPaymentWidgets;
}

/** SDK 전역 함수 — ANONYMOUS 는 비회원(카드 저장 미사용) 고객키 상수 */
interface TossPaymentsSdk {
  (clientKey: string): TossPaymentsInstance;
  ANONYMOUS: string;
}

declare global {
  interface Window {
    TossPayments?: TossPaymentsSdk;
  }
}

/** SDK 스크립트 로드는 1회만 — 결제 페이지 재진입 시 중복 삽입을 막는다 */
let loading: Promise<TossPaymentsSdk> | null = null;

const injectScript = (): Promise<TossPaymentsSdk> =>
  new Promise((resolve, reject) => {
    const script = document.createElement('script');
    script.src = SDK_URL;
    script.async = true;
    script.onload = () => {
      const sdk = window.TossPayments;
      if (sdk) resolve(sdk);
      else reject(new Error('결제 모듈을 초기화할 수 없습니다.'));
    };
    script.onerror = () => {
      loading = null; // 실패한 로드는 캐시하지 않는다 — 재시도 가능하게
      reject(new Error('결제 모듈을 불러오지 못했습니다. 잠시 후 다시 시도해주세요.'));
    };
    document.body.appendChild(script);
  });

/** 결제위젯 SDK 로드 — 이미 로드됐으면 즉시 반환한다 */
export const loadTossPayments = (): Promise<TossPaymentsSdk> => {
  if (window.TossPayments) return Promise.resolve(window.TossPayments);
  if (!loading) loading = injectScript();
  return loading;
};

/** 결제위젯 클라이언트 키 — 미설정이면 결제 화면이 안내 문구를 띄운다 */
export const tossClientKey = (): string => import.meta.env.VITE_TOSS_CLIENT_KEY ?? '';

/** 결제 성공·실패 리다이렉트 URL — SDK 가 쿼리로 결과를 붙여 돌려준다 */
export const paymentRedirectUrls = () => ({
  successUrl: `${window.location.origin}/payments/success`,
  failUrl: `${window.location.origin}/payments/fail`,
});
