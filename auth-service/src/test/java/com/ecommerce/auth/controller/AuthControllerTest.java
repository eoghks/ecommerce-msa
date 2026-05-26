package com.ecommerce.auth.controller;

import com.ecommerce.auth.domain.Role;
import com.ecommerce.auth.domain.User;
import com.ecommerce.auth.dto.SignupResponse;
import com.ecommerce.auth.jwt.JwtProvider;
import com.ecommerce.auth.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtProvider jwtProvider;

    @Test
    @DisplayName("회원가입 성공 - 201 반환")
    @WithMockUser
    void signup_success_returns201() throws Exception {
        User user = User.builder()
                .email("test@example.com")
                .password("hashed")
                .name("테스터")
                .role(Role.USER)
                .build();
        ReflectionTestUtils.setField(user, "id", 1L);
        given(authService.signup(any())).willReturn(new SignupResponse(user));

        Map<String, String> body = Map.of(
                "email", "test@example.com",
                "password", "Password123!",
                "name", "테스터"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.name").value("테스터"));
    }

    @Test
    @DisplayName("이메일 없이 요청 시 400 반환")
    @WithMockUser
    void signup_missingEmail_returns400() throws Exception {
        Map<String, String> body = Map.of(
                "password", "Password123!",
                "name", "테스터"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("중복 이메일 시 400 반환")
    @WithMockUser
    void signup_duplicateEmail_returns400() throws Exception {
        willThrow(new IllegalArgumentException("이미 사용 중인 이메일입니다."))
                .given(authService).signup(any());

        Map<String, String> body = Map.of(
                "email", "dup@example.com",
                "password", "Password123!",
                "name", "중복"
        );

        mockMvc.perform(post("/api/v1/auth/signup")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("이미 사용 중인 이메일입니다."));
    }
}