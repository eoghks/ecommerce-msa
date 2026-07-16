package com.ecommerce.order.service;

import com.ecommerce.order.domain.Address;
import com.ecommerce.order.dto.request.AddressRequest;
import com.ecommerce.order.dto.response.AddressResponse;
import com.ecommerce.order.exception.AddressNotFoundException;
import com.ecommerce.order.exception.UnauthorizedException;
import com.ecommerce.order.repository.AddressRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 배송지 주소록 서비스.
 * - 전 API 본인(userId) 소유만 접근 (IDOR 방지). userId 부재 → 401, 타인 소유 → 404.
 * - 기본 배송지 유일성 보장: 새 기본 지정/첫 주소 시 기존 기본 해제.
 */
@Service
@RequiredArgsConstructor
public class AddressService {

    private final AddressRepository addressRepository;

    /** 내 주소록 조회 — 기본 우선, 이후 최신순 */
    @Transactional(readOnly = true)
    public List<AddressResponse> getMyAddresses(Long userId) {
        requireUser(userId);
        return addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                .map(AddressResponse::from)
                .toList();
    }

    /** 배송지 추가 — 첫 주소는 자동으로 기본 배송지로 지정 */
    @Transactional
    public AddressResponse addAddress(Long userId, AddressRequest request) {
        requireUser(userId);
        boolean first = !addressRepository.existsByUserId(userId);
        Address address = Address.builder()
                .userId(userId)
                .receiver(request.receiver())
                .phone(request.phone())
                .address(request.address())
                .isDefault(first)
                .build();
        return AddressResponse.from(addressRepository.save(address));
    }

    /** 배송지 수정 (본인 소유만) */
    @Transactional
    public AddressResponse updateAddress(Long userId, Long addressId, AddressRequest request) {
        Address address = findOwned(userId, addressId);
        address.update(request.receiver(), request.phone(), request.address());
        return AddressResponse.from(address);
    }

    /** 배송지 삭제 (본인 소유만) — 기본 배송지 삭제 시 남은 주소 중 최신 1건을 기본으로 자동 승격 */
    @Transactional
    public void deleteAddress(Long userId, Long addressId) {
        Address address = findOwned(userId, addressId);
        boolean wasDefault = address.isDefault();
        addressRepository.delete(address);
        if (wasDefault) {
            // 유일성 인덱스 충돌 방지: 삭제를 먼저 DB 에 반영한 뒤 다른 주소를 기본으로 승격한다.
            addressRepository.flush();
            addressRepository.findByUserIdOrderByIsDefaultDescCreatedAtDesc(userId).stream()
                    .findFirst()
                    .ifPresent(Address::markDefault);
        }
    }

    /** 기본 배송지 지정 (본인 소유만) — 기존 기본은 해제하여 유일성 보장 */
    @Transactional
    public AddressResponse setDefault(Long userId, Long addressId) {
        Address target = findOwned(userId, addressId);
        addressRepository.findByUserIdAndIsDefaultTrue(userId)
                .filter(current -> !current.getId().equals(target.getId()))
                .ifPresent(current -> {
                    current.unmarkDefault();
                    // 유일성 인덱스 충돌 방지: 기존 기본 해제를 먼저 DB 에 반영한 뒤 신규를 지정한다.
                    addressRepository.flush();
                });
        target.markDefault();
        return AddressResponse.from(target);
    }

    /** userId 부재 → 401 */
    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("인증이 필요합니다.");
        }
    }

    /** 본인 소유 배송지 조회 — 없거나 타인 소유면 404 (정보 노출 방지) */
    private Address findOwned(Long userId, Long addressId) {
        requireUser(userId);
        return addressRepository.findById(addressId)
                .filter(a -> a.isOwnedBy(userId))
                .orElseThrow(() -> new AddressNotFoundException(addressId));
    }
}
