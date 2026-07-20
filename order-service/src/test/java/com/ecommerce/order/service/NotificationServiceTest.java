package com.ecommerce.order.service;

import com.ecommerce.order.domain.Notification;
import com.ecommerce.order.domain.NotificationType;
import com.ecommerce.order.dto.response.NotificationResponse;
import com.ecommerce.order.exception.NotificationNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.NotificationRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService 단위 테스트")
class NotificationServiceTest {

    @InjectMocks private NotificationService notificationService;
    @Mock       private NotificationRepository notificationRepository;

    private static final Long USER_ID  = 1L;
    private static final Long OTHER_ID = 2L;

    // ── 생성 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("create — 타입별 상수 템플릿으로 title/message 조립 후 저장")
    void create_buildsFromTemplate() {
        notificationService.create(USER_ID, NotificationType.ORDER_CONFIRMED, 100L);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        then(notificationRepository).should().save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getType()).isEqualTo(NotificationType.ORDER_CONFIRMED);
        assertThat(saved.getTitle()).isEqualTo("주문 확정");
        assertThat(saved.getMessage()).contains("100");
        assertThat(saved.getOrderId()).isEqualTo(100L);
        assertThat(saved.isRead()).isFalse();
    }

    // ── 목록 조회 ────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyNotifications — 본인 알림만 최신순 페이징 조회")
    void getMyNotifications_success() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<Notification> page = new PageImpl<>(
                List.of(notification(10L, USER_ID, NotificationType.ORDER_CONFIRMED, 100L)), pageable, 1);
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .willReturn(page);

        Page<NotificationResponse> result = notificationService.getMyNotifications(USER_ID, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).type()).isEqualTo(NotificationType.ORDER_CONFIRMED);
    }

    @Test
    @DisplayName("getMyNotifications — 알림 없으면 빈 페이지")
    void getMyNotifications_empty() {
        PageRequest pageable = PageRequest.of(0, 20);
        given(notificationRepository.findByUserIdOrderByCreatedAtDesc(USER_ID, pageable))
                .willReturn(Page.empty(pageable));

        assertThat(notificationService.getMyNotifications(USER_ID, pageable).getContent()).isEmpty();
    }

    @Test
    @DisplayName("getMyNotifications — userId null → 401")
    void getMyNotifications_noUser_unauthorized() {
        assertThatThrownBy(() -> notificationService.getMyNotifications(null, PageRequest.of(0, 20)))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 미읽음 수 ────────────────────────────────────────────────────

    @Test
    @DisplayName("getUnreadCount — 본인 미읽음 개수 반환")
    void getUnreadCount_success() {
        given(notificationRepository.countByUserIdAndIsReadFalse(USER_ID)).willReturn(3L);

        assertThat(notificationService.getUnreadCount(USER_ID)).isEqualTo(3L);
    }

    @Test
    @DisplayName("getUnreadCount — userId null → 401")
    void getUnreadCount_noUser_unauthorized() {
        assertThatThrownBy(() -> notificationService.getUnreadCount(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 단건 읽음 ────────────────────────────────────────────────────

    @Test
    @DisplayName("markAsRead — 본인 알림 읽음 처리")
    void markAsRead_success() {
        Notification n = notification(10L, USER_ID, NotificationType.ORDER_CONFIRMED, 100L);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        notificationService.markAsRead(USER_ID, 10L);

        assertThat(n.isRead()).isTrue();
    }

    @Test
    @DisplayName("markAsRead — 타인 알림 읽음 시도 → 404 (본인 격리)")
    void markAsRead_otherUser_notFound() {
        Notification n = notification(10L, OTHER_ID, NotificationType.ORDER_CONFIRMED, 100L);
        given(notificationRepository.findById(10L)).willReturn(Optional.of(n));

        assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, 10L))
                .isInstanceOf(NotificationNotFoundException.class);
        assertThat(n.isRead()).isFalse();
    }

    @Test
    @DisplayName("markAsRead — 존재하지 않는 알림 → 404")
    void markAsRead_notFound() {
        given(notificationRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> notificationService.markAsRead(USER_ID, 10L))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    @Test
    @DisplayName("markAsRead — userId null → 401")
    void markAsRead_noUser_unauthorized() {
        assertThatThrownBy(() -> notificationService.markAsRead(null, 10L))
                .isInstanceOf(UnauthorizedException.class);
        then(notificationRepository).should(never()).findById(any());
    }

    // ── 전체 읽음 ────────────────────────────────────────────────────

    @Test
    @DisplayName("markAllRead — 본인 미읽음 알림 일괄 갱신")
    void markAllRead_success() {
        notificationService.markAllRead(USER_ID);

        then(notificationRepository).should().markAllRead(USER_ID);
    }

    @Test
    @DisplayName("markAllRead — userId null → 401")
    void markAllRead_noUser_unauthorized() {
        assertThatThrownBy(() -> notificationService.markAllRead(null))
                .isInstanceOf(UnauthorizedException.class);
        then(notificationRepository).should(never()).markAllRead(any());
    }

    // ── helper ────────────────────────────────────────────────────────

    private Notification notification(Long id, Long userId, NotificationType type, Long orderId) {
        Notification n = Notification.builder()
                .userId(userId)
                .type(type)
                .title(type.getTitle())
                .message(type.formatMessage(orderId))
                .orderId(orderId)
                .build();
        ReflectionTestUtils.setField(n, "id", id);
        return n;
    }
}
