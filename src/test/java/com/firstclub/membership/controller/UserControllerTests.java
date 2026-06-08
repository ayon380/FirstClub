package com.firstclub.membership.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firstclub.membership.domain.entity.User;
import com.firstclub.membership.dto.request.CreateUserRequest;
import com.firstclub.membership.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class UserControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void createUser_Success() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        req.setName("Test User");
        req.setEmail("test@user.com");
        req.setCohort("TEST_COHORT");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.email").value("test@user.com"))
                .andExpect(jsonPath("$.cohort").value("TEST_COHORT"));

        assertThat(userRepository.findByEmail("test@user.com")).isPresent();
    }

    @Test
    void createUser_DuplicateEmail_ThrowsConflict() throws Exception {
        userRepository.save(User.builder().name("Existing").email("test@user.com").build());

        CreateUserRequest req = new CreateUserRequest();
        req.setName("Test User");
        req.setEmail("test@user.com");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Email already in use: test@user.com"));
    }

    @Test
    void createUser_InvalidInputs_ReturnsBadRequest() throws Exception {
        CreateUserRequest req = new CreateUserRequest();
        // blank fields, invalid email will trigger validation errors
        req.setName("");
        req.setEmail("invalid-email");

        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getUserById_Success() throws Exception {
        User user = userRepository.save(User.builder().name("Integration User").email("int@user.com").build());

        mockMvc.perform(get("/api/users/" + user.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(user.getId()))
                .andExpect(jsonPath("$.name").value("Integration User"))
                .andExpect(jsonPath("$.email").value("int@user.com"));
    }

    @Test
    void getUserById_NotFound_ReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/api/users/99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("User not found: 99999"));
    }

    @Test
    void getAllUsers_Success() throws Exception {
        userRepository.save(User.builder().name("User 1").email("user1@test.com").build());
        userRepository.save(User.builder().name("User 2").email("user2@test.com").build());

        mockMvc.perform(get("/api/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$.length()").value(2));
    }
}
