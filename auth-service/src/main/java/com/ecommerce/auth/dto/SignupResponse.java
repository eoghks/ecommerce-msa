package com.ecommerce.auth.dto;

import com.ecommerce.auth.domain.User;
import lombok.Getter;

@Getter
public class SignupResponse {

    private final Long id;
    private final String email;
    private final String name;

    public SignupResponse(User user) {
        this.id    = user.getId();
        this.email = user.getEmail();
        this.name  = user.getName();
    }
}
