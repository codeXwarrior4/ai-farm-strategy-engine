package com.farmengine.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.farmengine.dto.LoginRequest;
import com.farmengine.dto.RegisterRequest;
import com.farmengine.entity.User;
import com.farmengine.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("POST /api/v1/auth/register should create user and return 201 with JWT token")
    void register_validRequest_returns201WithToken() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("newfarmer")
                .email("newfarmer@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.username").value("newfarmer"))
                .andExpect(jsonPath("$.email").value("newfarmer@example.com"))
                .andExpect(jsonPath("$.role").value("ROLE_FARMER"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 409 for duplicate username")
    void register_duplicateUsername_returns409() throws Exception {
        userRepository.save(new User("existinguser", "unique1@example.com", passwordEncoder.encode("Pass1234!")));

        RegisterRequest req = RegisterRequest.builder()
                .username("existinguser")
                .email("another@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already taken")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 409 for duplicate email")
    void register_duplicateEmail_returns409() throws Exception {
        userRepository.save(new User("uniqueuser", "existing@example.com", passwordEncoder.encode("Pass1234!")));

        RegisterRequest req = RegisterRequest.builder()
                .username("newusername")
                .email("existing@example.com")
                .password("Password123!")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("already registered")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should return 400 for invalid inputs")
    void register_invalidInputs_returns400() throws Exception {
        RegisterRequest req = RegisterRequest.builder()
                .username("ab") // too short
                .email("not-an-email")
                .password("short") // too short
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.fieldErrors.username").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 200 with JWT for valid credentials")
    void login_validCredentials_returns200WithToken() throws Exception {
        userRepository.save(new User("loginuser", "login@example.com", passwordEncoder.encode("SecretPass1!")));

        LoginRequest req = LoginRequest.builder()
                .usernameOrEmail("loginuser")
                .password("SecretPass1!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.username").value("loginuser"))
                .andExpect(jsonPath("$.email").value("login@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should support login with email")
    void login_withEmail_returns200() throws Exception {
        userRepository.save(new User("useremail", "emailtest@example.com", passwordEncoder.encode("SecretPass1!")));

        LoginRequest req = LoginRequest.builder()
                .usernameOrEmail("emailtest@example.com")
                .password("SecretPass1!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString())
                .andExpect(jsonPath("$.email").value("emailtest@example.com"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 401 for wrong password")
    void login_wrongPassword_returns401() throws Exception {
        userRepository.save(new User("wrongpassuser", "wrongpass@example.com", passwordEncoder.encode("SecretPass1!")));

        LoginRequest req = LoginRequest.builder()
                .usernameOrEmail("wrongpassuser")
                .password("WrongPassword123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value(containsString("Invalid username or password")));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login should return 401 for non-existent user")
    void login_nonExistentUser_returns401() throws Exception {
        LoginRequest req = LoginRequest.builder()
                .usernameOrEmail("doesnotexist")
                .password("SomePassword123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
