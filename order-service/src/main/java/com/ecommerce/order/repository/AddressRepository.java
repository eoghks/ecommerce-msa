package com.ecommerce.order.repository;

import com.ecommerce.order.domain.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AddressRepository extends JpaRepository<Address, Long> {

    /** 내 주소록 — 기본 배송지 우선, 이후 최신순 */
    List<Address> findByUserIdOrderByIsDefaultDescCreatedAtDesc(Long userId);

    /** 현재 기본 배송지 (유일성은 서비스에서 보장) */
    Optional<Address> findByUserIdAndIsDefaultTrue(Long userId);

    /** 사용자 주소 보유 여부 — 첫 주소 자동 기본 판정용 */
    boolean existsByUserId(Long userId);
}
