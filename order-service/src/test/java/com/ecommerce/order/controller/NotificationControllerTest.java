package com.ecommerce.order.controller;

import com.ecommerce.order.dto.response.NotificationResponse;
import com.ecommerce.order.exception.NotificationNotFoundException;
import com.ecommerce.order.exception.OrderExceptionHandler;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.ecommerce.order.domain.NotificationType;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationController 단위 테스트")
class NotificationControllerTest {

    @InjectMocks private NotificationController notificationController;
    @Mock        private NotificationService notificationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(notificationController)
                .setControllerAdvice(new OrderExceptionHandler())
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .build();
    }

    @Test
    @DisplayName("GET /me — 본인 알림 목록 200")
    void getMyNotifications_ok() throws Exception {
        NotificationResponse res = new NotificationResponse(
                10L, NotificationType.ORDER_CONFIRMED, "주문 확정",
                "주문이 확정되었습니다. (주문 #100)", 100L, false, LocalDateTime.now());
        Page<NotificationResponse> page = new PageImpl<>(List.of(res), PageRequest.of(0, 20), 1);
        given(notificationService.getMyNotifications(eq(1L), any())).willReturn(page);

        mockMvc.perform(get("/api/v1/notifications/me").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].type").value("ORDER_CONFIRMED"))
                .andExpect(jsonPath("$.content[0].orderId").value(100));
    }

    @Test
    @DisplayName("GET /me/unread-count — 미읽음 개수 200")
    void getUnreadCount_ok() throws Exception {
        given(notificationService.getUnreadCount(1L)).willReturn(5L);

        mockMvc.perform(get("/api/v1/notifications/me/unread-count").header("X-User-Id", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    @DisplayName("PATCH /{id}/read — 단건 읽음 204")
    void markAsRead_noContent() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/10/read").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());

        then(notificationService).should().markAsRead(1L, 10L);
    }

    @Test
    @DisplayName("PATCH /read-all — 전체 읽음 204")
    void markAllRead_noContent() throws Exception {
        mockMvc.perform(patch("/api/v1/notifications/read-all").header("X-User-Id", "1"))
                .andExpect(status().isNoContent());

        then(notificationService).should().markAllRead(1L);
    }

    @Test
    @DisplayName("GET /me — X-User-Id 없음 → 401")
    void getMyNotifications_noUser_unauthorized() throws Exception {
        given(notificationService.getMyNotifications(isNull(), any()))
                .willThrow(new UnauthorizedException("인증이 필요합니다."));

        mockMvc.perform(get("/api/v1/notifications/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PATCH /{id}/read — 타인 알림 읽음 시도 → 404")
    void markAsRead_otherUser_notFound() throws Exception {
        willThrow(new NotificationNotFoundException(10L))
                .given(notificationService).markAsRead(1L, 10L);

        mockMvc.perform(patch("/api/v1/notifications/10/read").header("X-User-Id", "1"))
                .andExpect(status().isNotFound());
    }
}
