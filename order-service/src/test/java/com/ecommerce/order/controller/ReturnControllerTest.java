package com.ecommerce.order.controller;

import com.ecommerce.order.domain.ReturnRequest;
import com.ecommerce.order.domain.ReturnStatus;
import com.ecommerce.order.dto.response.ReturnResponse;
import com.ecommerce.order.exception.DuplicateReturnRequestException;
import com.ecommerce.order.exception.InvalidReturnStatusException;
import com.ecommerce.order.exception.OrderExceptionHandler;
import com.ecommerce.order.exception.OrderNotFoundException;
import com.ecommerce.order.exception.ReturnAccessDeniedException;
import com.ecommerce.order.exception.ReturnNotAllowedException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.service.ReturnService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("ReturnController 단위 테스트 (V1.1-5)")
class ReturnControllerTest {

    @InjectMocks private ReturnController returnController;
    @Mock        private ReturnService    returnService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(returnController)
                .setControllerAdvice(new OrderExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    // ── 신청 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST 반품 신청 — 201 + REQUESTED 응답")
    void requestReturn_created() throws Exception {
        given(returnService.request(1L, 2L, 5L, "제품 하자"))
                .willReturn(response(ReturnStatus.REQUESTED, null));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"제품 하자\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("REQUESTED"))
                .andExpect(jsonPath("$.orderItemId").value(2));
    }

    @Test
    @DisplayName("POST 반품 신청 — 사유 누락(빈 값) → 400")
    void requestReturn_blankReason_badRequest() throws Exception {
        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST 반품 신청 — 자격 미충족(배송완료 아님) → 400")
    void requestReturn_notDelivered_badRequest() throws Exception {
        given(returnService.request(1L, 2L, 5L, "단순 변심"))
                .willThrow(new ReturnNotAllowedException("배송 완료된 주문만 반품할 수 있습니다."));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"단순 변심\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("배송 완료된 주문만 반품할 수 있습니다."));
    }

    @Test
    @DisplayName("POST 반품 신청 — 중복 신청 → 409")
    void requestReturn_duplicate_conflict() throws Exception {
        given(returnService.request(1L, 2L, 5L, "제품 하자"))
                .willThrow(new DuplicateReturnRequestException(2L));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"제품 하자\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST 반품 신청(H-2) — 동시 신청 경쟁으로 유니크 제약 위반 → 409")
    void requestReturn_uniqueViolation_conflict() throws Exception {
        given(returnService.request(1L, 2L, 5L, "제품 하자"))
                .willThrow(new DataIntegrityViolationException(
                        "duplicate key value violates unique constraint \"uq_return_item_active\""));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"제품 하자\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Data Integrity Violation"));
    }

    @Test
    @DisplayName("POST 반품 신청 — 타인 주문 → 404")
    void requestReturn_otherOrder_notFound() throws Exception {
        given(returnService.request(1L, 2L, 5L, "제품 하자"))
                .willThrow(new OrderNotFoundException(1L));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .header("X-User-Id", "5")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"제품 하자\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST 반품 신청 — X-User-Id 없음 → 401")
    void requestReturn_noUser_unauthorized() throws Exception {
        given(returnService.request(eq(1L), eq(2L), isNull(), eq("제품 하자")))
                .willThrow(new UnauthorizedException("인증이 필요합니다."));

        mockMvc.perform(post("/api/v1/orders/1/items/2/returns")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"제품 하자\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ── 조회 ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /returns/me — 내 반품 목록 200")
    void getMyReturns_ok() throws Exception {
        Page<ReturnResponse> page = new PageImpl<>(
                List.of(response(ReturnStatus.REQUESTED, null)), PageRequest.of(0, 20), 1);
        given(returnService.getMyReturns(eq(5L), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/returns/me").header("X-User-Id", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.content[0].orderId").value(1));
    }

    @Test
    @DisplayName("GET /returns/me — X-User-Id 없음 → 401")
    void getMyReturns_noUser_unauthorized() throws Exception {
        given(returnService.getMyReturns(isNull(), any()))
                .willThrow(new UnauthorizedException("인증이 필요합니다."));

        mockMvc.perform(get("/api/v1/returns/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /returns/admin — 관리 목록 200 (ADMIN)")
    void getManagedReturns_ok() throws Exception {
        Page<ReturnResponse> page = new PageImpl<>(
                List.of(response(ReturnStatus.REQUESTED, null)), PageRequest.of(0, 20), 1);
        given(returnService.getManagedReturns(eq(999L), eq("ADMIN"), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/returns/admin")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /returns/admin — 권한 없는 사용자 → 403")
    void getManagedReturns_forbidden() throws Exception {
        given(returnService.getManagedReturns(eq(5L), eq("USER"), any()))
                .willThrow(new ReturnAccessDeniedException("반품을 조회할 권한이 없습니다."));

        mockMvc.perform(get("/api/v1/returns/admin")
                        .header("X-User-Id", "5")
                        .header("X-User-Role", "USER"))
                .andExpect(status().isForbidden());
    }

    // ── 승인·거부 ────────────────────────────────────────────────

    @Test
    @DisplayName("PATCH /returns/{id}/approve — 200 + REFUNDED 응답")
    void approveReturn_ok() throws Exception {
        given(returnService.approve(10L, 999L, "ADMIN"))
                .willReturn(response(ReturnStatus.REFUNDED, null));

        mockMvc.perform(patch("/api/v1/returns/10/approve")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    @DisplayName("PATCH /returns/{id}/approve — REQUESTED 아님 → 400")
    void approveReturn_invalidStatus_badRequest() throws Exception {
        given(returnService.approve(10L, 999L, "ADMIN"))
                .willThrow(new InvalidReturnStatusException("승인할 수 없는 반품 상태입니다."));

        mockMvc.perform(patch("/api/v1/returns/10/approve")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /returns/{id}/approve — 타 판매자 → 403")
    void approveReturn_otherSeller_forbidden() throws Exception {
        given(returnService.approve(10L, 8L, "SELLER"))
                .willThrow(new ReturnAccessDeniedException("반품을 처리할 권한이 없습니다. id=10"));

        mockMvc.perform(patch("/api/v1/returns/10/approve")
                        .header("X-User-Id", "8")
                        .header("X-User-Role", "SELLER"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /returns/{id}/approve(M-1) — 동시 승인 낙관적 락 충돌 → 409")
    void approveReturn_optimisticLockConflict() throws Exception {
        given(returnService.approve(10L, 999L, "ADMIN"))
                .willThrow(new ObjectOptimisticLockingFailureException(ReturnRequest.class, 10L));

        mockMvc.perform(patch("/api/v1/returns/10/approve")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Concurrent Modification"));
    }

    @Test
    @DisplayName("PATCH /returns/{id}/reject — 200 + REJECTED 응답")
    void rejectReturn_ok() throws Exception {
        given(returnService.reject(10L, 999L, "ADMIN", "사용 흔적 있음"))
                .willReturn(response(ReturnStatus.REJECTED, "사용 흔적 있음"));

        mockMvc.perform(patch("/api/v1/returns/10/reject")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"사용 흔적 있음\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.rejectReason").value("사용 흔적 있음"));
    }

    @Test
    @DisplayName("PATCH /returns/{id}/reject — 거부 사유 누락 → 400")
    void rejectReturn_blankReason_badRequest() throws Exception {
        mockMvc.perform(patch("/api/v1/returns/10/reject")
                        .header("X-User-Id", "999")
                        .header("X-User-Role", "ADMIN")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rejectReason\":\"\"}"))
                .andExpect(status().isBadRequest());

        then(returnService).shouldHaveNoInteractions();
    }

    // ── helper ──────────────────────────────────────────────────────

    private ReturnResponse response(ReturnStatus status, String rejectReason) {
        return new ReturnResponse(10L, 1L, 2L, 5L, "제품 하자", status, rejectReason,
                LocalDateTime.now(), status == ReturnStatus.REQUESTED ? null : LocalDateTime.now());
    }
}
