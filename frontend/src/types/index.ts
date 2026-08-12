/**
 * 공용 도메인·API 타입 정의 (B-04 TS 마이그레이션).
 * 실제 백엔드 응답 형태와 프론트 사용처를 기준으로 정의한다.
 */

// ── 공통 ─────────────────────────────────────────────

/** 서버 오류 응답 본문 — ProblemDetail(detail) 또는 일반 메시지(message) */
export interface ApiErrorResponse {
  detail?: string;
  message?: string;
}

/** Spring Data 페이지 응답 */
export interface Page<T> {
  content: T[];
  totalPages: number;
  totalElements: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

// ── 인증·사용자 ──────────────────────────────────────

/** 사용자 권한 (백엔드 Role enum) */
export type Role = 'USER' | 'SELLER' | 'ADMIN';

/** JWT 클레임 (검증 없이 파싱한 payload) */
export interface JwtClaims {
  sub?: string;
  role?: Role;
  pwdChangeRequired?: boolean;
}

/** 로그인/리프레시 응답 (access token 발급) */
export interface AuthTokenResponse {
  accessToken: string;
}

/** 판매자 신청 응답 — 새 JWT + 안내 메시지 */
export interface SellerApplyResponse {
  accessToken: string;
  message: string;
}

/** 이메일 중복 확인 응답 */
export interface CheckEmailResponse {
  available: boolean;
}

/** 비밀번호 찾기 응답 — 테스트 서버는 임시 비밀번호를 즉시 반환 */
export interface ForgotPasswordResponse {
  tempPassword?: string;
}

/** 내 프로필 (getMe) */
export interface UserProfile {
  email: string;
  name: string;
  role: Role;
  phone?: string;
  createdAt?: string;
}

// ── 카테고리·상품 ────────────────────────────────────

export interface Category {
  id: number;
  name: string;
}

/** 상품 판매 상태 (백엔드 ProductStatus enum) */
export type ProductStatus = 'ACTIVE' | 'BANNED';

/** 상품 상세/목록 항목 */
export interface Product {
  id: number;
  name: string;
  description?: string;
  price: number;
  stock: number;
  imageUrl?: string | null;
  categoryId: number;
  categoryName?: string;
  status?: ProductStatus;
  sellerId?: number | null;
  sellerName?: string | null;
  sellerEmail?: string | null;
  ratingAvg?: number;
  ratingCount?: number;
  createdAt?: string;
}

/** 이미지 업로드 응답 */
export interface ProductImageUploadResponse {
  url: string;
}

/** 상품 등록/수정 요청 페이로드 */
export interface ProductPayload {
  name: string;
  description: string;
  price: number;
  stock: number;
  imageUrl: string | null;
  categoryId: number;
}

// ── 리뷰 ─────────────────────────────────────────────

export interface Review {
  reviewId: number;
  userId: number;
  rating: number;
  content?: string;
  createdAt?: string;
  updatedAt?: string | null;
}

/** 리뷰 작성/수정 요청 본문 */
export interface ReviewInput {
  rating: number;
  content: string;
}

// ── 장바구니 ─────────────────────────────────────────

export interface CartItem {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  imageUrl?: string | null;
}

/** 장바구니 조회 응답 */
export interface Cart {
  items: CartItem[];
}

// ── 주문 ─────────────────────────────────────────────

/** 주문 상태 (백엔드 OrderStatus enum) */
export type OrderStatus = 'PENDING' | 'CONFIRMED' | 'PARTIALLY_CANCELLED' | 'CANCELLED';

/** 배송 상태 (백엔드 DeliveryStatus enum) */
export type DeliveryStatus = 'PREPARING' | 'SHIPPING' | 'DELIVERED';

/** 주문 항목 상태 (백엔드 OrderItemStatus enum) */
export type OrderItemStatus = 'ACTIVE' | 'CANCELLED';

export interface OrderItem {
  id: number;
  productId: number;
  productName: string;
  price: number;
  quantity: number;
  subtotal: number;
  status: OrderItemStatus;
  cancelReason?: string | null;
  sellerId?: number | null;
}

export interface Order {
  id: number;
  status: OrderStatus;
  deliveryStatus?: DeliveryStatus;
  items: OrderItem[];
  totalPrice: number;
  receiver?: string;
  phone?: string;
  address?: string;
  createdAt?: string;
}

/** 주문 생성 시 전달하는 상품 항목 */
export interface OrderRequestItem {
  productId: number;
  productName: string;
  price: number;
  quantity: number;
}

/** 주문 배송지 페이로드 — 저장된 배송지(addressId) 또는 직접입력 */
export interface OrderAddressPayload {
  addressId?: number;
  receiver?: string;
  phone?: string;
  address?: string;
}

/** 자동취소(실패) 주문 항목 (M-3) */
export interface FailedOrder {
  orderId: number;
  userId: number;
  reason: string;
  occurredAt?: string;
}

// ── 반품·환불 ────────────────────────────────────────

/** 반품 진행 상태 (백엔드 ReturnStatus enum) — REQUESTED→APPROVED→REFUNDED / REQUESTED→REJECTED */
export type ReturnStatus = 'REQUESTED' | 'APPROVED' | 'REJECTED' | 'REFUNDED';

/** 반품 신청 내역 (백엔드 ReturnResponse) */
export interface ReturnRequest {
  id: number;
  orderId: number;
  orderItemId: number;
  userId: number;
  reason: string;
  status: ReturnStatus;
  rejectReason?: string | null;
  requestedAt?: string;
  processedAt?: string | null;
}

// ── 배송지 ───────────────────────────────────────────

export interface Address {
  id: number;
  receiver: string;
  phone: string;
  address: string;
  isDefault: boolean;
}

/** 배송지 추가/수정 입력 */
export interface AddressInput {
  receiver: string;
  phone: string;
  address: string;
}

// ── 찜(위시리스트) ───────────────────────────────────

/** 찜 목록 항목 — 상품 스냅샷 */
export interface WishlistItem {
  productId: number;
  name: string;
  price: number;
  imageUrl?: string | null;
  status?: string;
  createdAt?: string;
}

// ── 알림 ─────────────────────────────────────────────

/** 알림 유형 (백엔드 NotificationType enum) */
export type NotificationType =
  | 'ORDER_CONFIRMED'
  | 'ORDER_CANCELLED'
  | 'ORDER_ITEM_CANCELLED'
  | 'DELIVERY_SHIPPING'
  | 'DELIVERY_DELIVERED'
  | 'RETURN_APPROVED'
  | 'RETURN_REJECTED'
  | 'RETURN_REFUNDED';

export interface Notification {
  id: number;
  title: string;
  message: string;
  isRead: boolean;
  type?: NotificationType;
  orderId?: number | null;
  createdAt?: string;
}

/** 미읽음 개수 응답 */
export interface UnreadCountResponse {
  count: number;
}

// ── 모니터링 ─────────────────────────────────────────

export interface ServiceHealth {
  name: string;
  status: string;
  responseTimeMs: number;
  error?: string | null;
}
