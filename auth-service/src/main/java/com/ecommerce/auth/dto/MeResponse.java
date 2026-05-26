package com.ecommerce.auth.dto;

import com.ecommerce.auth.domain.User;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class MeResponse {

    private final Long id;
    private final String email;
    private final String name;
    private final String role;
    private final LocalDateTime createdAt;

    public MeResponse(User user) {
        this.id        = user.getId();
        this.email     = user.getEmail();
        this.name      = user.getName();
        this.role      = user.getRole().name();
        this.createdAt = user.getCreatedAt();
    }
}
