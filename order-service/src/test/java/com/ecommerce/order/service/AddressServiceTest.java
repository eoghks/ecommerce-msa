package com.ecommerce.order.service;

import com.ecommerce.order.domain.Address;
import com.ecommerce.order.dto.request.AddressRequest;
import com.ecommerce.order.dto.response.AddressResponse;
import com.ecommerce.order.exception.AddressNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.AddressRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
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
@DisplayName("AddressService 단위 테스트")
class AddressServiceTest {

    @InjectMocks private AddressService addressService;
    @Mock       private AddressRepository addressRepository;

    private static final Long USER_ID  = 1L;
    private static final Long OTHER_ID  = 2L;

    private final AddressRequest request =
            new AddressRequest("홍길동", "010-1234-5678", "서울시 강남구");

    // ── 목록 조회 ────────────────────────────────────────────────────

    @Test
    @DisplayName("getMyAddresses — 기본 우선 정렬로 본인 주소만 조회")
    void getMyAddresses_success() {
        given(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .willReturn(List.of(address(10L, USER_ID, true)));

        List<AddressResponse> result = addressService.getMyAddresses(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isDefault()).isTrue();
    }

    @Test
    @DisplayName("getMyAddresses — 주소 없으면 빈 목록")
    void getMyAddresses_empty() {
        given(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .willReturn(List.of());

        assertThat(addressService.getMyAddresses(USER_ID)).isEmpty();
    }

    @Test
    @DisplayName("getMyAddresses — userId null → 401")
    void getMyAddresses_noUser_unauthorized() {
        assertThatThrownBy(() -> addressService.getMyAddresses(null))
                .isInstanceOf(UnauthorizedException.class);
    }

    // ── 추가 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addAddress — 첫 주소는 자동으로 기본 배송지")
    void addAddress_firstIsDefault() {
        given(addressRepository.existsByUserId(USER_ID)).willReturn(false);
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

        addressService.addAddress(USER_ID, request);

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        then(addressRepository).should().save(captor.capture());
        assertThat(captor.getValue().isDefault()).isTrue();
    }

    @Test
    @DisplayName("addAddress — 두 번째 주소는 기본 아님")
    void addAddress_secondNotDefault() {
        given(addressRepository.existsByUserId(USER_ID)).willReturn(true);
        given(addressRepository.save(any(Address.class))).willAnswer(inv -> inv.getArgument(0));

        addressService.addAddress(USER_ID, request);

        ArgumentCaptor<Address> captor = ArgumentCaptor.forClass(Address.class);
        then(addressRepository).should().save(captor.capture());
        assertThat(captor.getValue().isDefault()).isFalse();
    }

    // ── 수정 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("updateAddress — 본인 주소 수정")
    void updateAddress_success() {
        Address address = address(10L, USER_ID, false);
        given(addressRepository.findById(10L)).willReturn(Optional.of(address));

        AddressResponse res = addressService.updateAddress(USER_ID, 10L,
                new AddressRequest("김철수", "010-0000-0000", "부산시"));

        assertThat(res.receiver()).isEqualTo("김철수");
        assertThat(res.address()).isEqualTo("부산시");
    }

    @Test
    @DisplayName("updateAddress — 타인 주소 수정 시 404 (본인 격리)")
    void updateAddress_otherUser_notFound() {
        given(addressRepository.findById(10L)).willReturn(Optional.of(address(10L, OTHER_ID, false)));

        assertThatThrownBy(() -> addressService.updateAddress(USER_ID, 10L, request))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // ── 삭제 ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteAddress — 본인 주소 삭제")
    void deleteAddress_success() {
        Address address = address(10L, USER_ID, false);
        given(addressRepository.findById(10L)).willReturn(Optional.of(address));

        addressService.deleteAddress(USER_ID, 10L);

        then(addressRepository).should().delete(address);
    }

    @Test
    @DisplayName("deleteAddress — 기본 배송지 삭제 시 남은 최신 주소를 기본으로 자동 승격")
    void deleteAddress_defaultPromotesRemaining() {
        Address target    = address(10L, USER_ID, true);
        Address remaining = address(20L, USER_ID, false);
        given(addressRepository.findById(10L)).willReturn(Optional.of(target));
        given(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .willReturn(List.of(remaining));

        addressService.deleteAddress(USER_ID, 10L);

        then(addressRepository).should().delete(target);
        then(addressRepository).should().flush();
        assertThat(remaining.isDefault()).isTrue();
    }

    @Test
    @DisplayName("deleteAddress — 기본 배송지 삭제 후 남은 주소 없으면 승격 없음")
    void deleteAddress_defaultNoRemaining() {
        Address target = address(10L, USER_ID, true);
        given(addressRepository.findById(10L)).willReturn(Optional.of(target));
        given(addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(USER_ID))
                .willReturn(List.of());

        addressService.deleteAddress(USER_ID, 10L);

        then(addressRepository).should().delete(target);
        then(addressRepository).should().flush();
    }

    @Test
    @DisplayName("deleteAddress — 기본 아닌 주소 삭제 시 승격 로직 미동작")
    void deleteAddress_nonDefaultNoPromotion() {
        Address target = address(10L, USER_ID, false);
        given(addressRepository.findById(10L)).willReturn(Optional.of(target));

        addressService.deleteAddress(USER_ID, 10L);

        then(addressRepository).should().delete(target);
        then(addressRepository).should(never()).flush();
    }

    @Test
    @DisplayName("deleteAddress — 존재하지 않는 주소 → 404")
    void deleteAddress_notFound() {
        given(addressRepository.findById(10L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> addressService.deleteAddress(USER_ID, 10L))
                .isInstanceOf(AddressNotFoundException.class);
        then(addressRepository).should(never()).delete(any());
    }

    // ── 기본 지정 (유일성) ────────────────────────────────────────────

    @Test
    @DisplayName("setDefault — 새 기본 지정 시 기존 기본 해제 (유일성 보장)")
    void setDefault_unmarksPreviousDefault() {
        Address previous = address(10L, USER_ID, true);
        Address target   = address(20L, USER_ID, false);
        given(addressRepository.findById(20L)).willReturn(Optional.of(target));
        given(addressRepository.findByUserIdAndIsDefaultTrue(USER_ID)).willReturn(Optional.of(previous));

        addressService.setDefault(USER_ID, 20L);

        assertThat(previous.isDefault()).isFalse();
        assertThat(target.isDefault()).isTrue();
    }

    @Test
    @DisplayName("setDefault — 타인 주소 기본 지정 시 404")
    void setDefault_otherUser_notFound() {
        given(addressRepository.findById(20L)).willReturn(Optional.of(address(20L, OTHER_ID, false)));

        assertThatThrownBy(() -> addressService.setDefault(USER_ID, 20L))
                .isInstanceOf(AddressNotFoundException.class);
    }

    // ── helper ────────────────────────────────────────────────────────

    private Address address(Long id, Long userId, boolean isDefault) {
        Address address = Address.builder()
                .userId(userId).receiver("홍길동").phone("010-1234-5678")
                .address("서울시 강남구").isDefault(isDefault).build();
        ReflectionTestUtils.setField(address, "id", id);
        return address;
    }
}
