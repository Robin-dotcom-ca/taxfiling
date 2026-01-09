package com.taxfiling.security;

import com.taxfiling.model.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JwtTokenProvider Tests")
class JwtTokenProviderTest {

    private JwtTokenProvider tokenProvider;
    private UserPrincipal testUser;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider();

        // Set test configuration via reflection
        ReflectionTestUtils.setField(tokenProvider, "jwtSecret",
                "test-secret-key-that-is-at-least-256-bits-long-for-hs256");
        ReflectionTestUtils.setField(tokenProvider, "jwtExpiration", 3600000L); // 1 hour
        ReflectionTestUtils.setField(tokenProvider, "refreshExpiration", 604800000L); // 7 days

        // Initialize the key
        tokenProvider.init();

        // Create test user
        testUser = UserPrincipal.builder()
                .id(UUID.randomUUID())
                .email("test@example.com")
                .password("password")
                .role(UserRole.TAXPAYER)
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_TAXPAYER")))
                .build();
    }

    @Test
    @DisplayName("Should generate valid access token")
    void shouldGenerateValidAccessToken() {
        String token = tokenProvider.generateAccessToken(testUser);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(tokenProvider.validateToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should generate valid refresh token")
    void shouldGenerateValidRefreshToken() {
        String token = tokenProvider.generateRefreshToken(testUser);

        assertThat(token).isNotNull();
        assertThat(token).isNotEmpty();
        assertThat(tokenProvider.validateToken(token)).isTrue();
        assertThat(tokenProvider.isRefreshToken(token)).isTrue();
    }

    @Test
    @DisplayName("Should extract user ID from token")
    void shouldExtractUserIdFromToken() {
        String token = tokenProvider.generateAccessToken(testUser);

        UUID extractedId = tokenProvider.getUserIdFromToken(token);

        assertThat(extractedId).isEqualTo(testUser.getId());
    }

    @Test
    @DisplayName("Should extract email from token")
    void shouldExtractEmailFromToken() {
        String token = tokenProvider.generateAccessToken(testUser);

        String extractedEmail = tokenProvider.getEmailFromToken(token);

        assertThat(extractedEmail).isEqualTo(testUser.getEmail());
    }

    @Test
    @DisplayName("Should identify refresh token correctly")
    void shouldIdentifyRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken(testUser);
        String refreshToken = tokenProvider.generateRefreshToken(testUser);

        assertThat(tokenProvider.isRefreshToken(accessToken)).isFalse();
        assertThat(tokenProvider.isRefreshToken(refreshToken)).isTrue();
    }

    @Test
    @DisplayName("Should reject invalid token")
    void shouldRejectInvalidToken() {
        assertThat(tokenProvider.validateToken("invalid.token.here")).isFalse();
        assertThat(tokenProvider.validateToken("")).isFalse();
        assertThat(tokenProvider.validateToken(null)).isFalse();
    }

    @Test
    @DisplayName("Should reject tampered token")
    void shouldRejectTamperedToken() {
        String token = tokenProvider.generateAccessToken(testUser);
        String tamperedToken = token.substring(0, token.length() - 5) + "xxxxx";

        assertThat(tokenProvider.validateToken(tamperedToken)).isFalse();
    }

    @Test
    @DisplayName("Should return correct expiration time")
    void shouldReturnCorrectExpirationTime() {
        long expirationInSeconds = tokenProvider.getExpirationInSeconds();

        assertThat(expirationInSeconds).isEqualTo(3600L); // 1 hour in seconds
    }

    @Test
    @DisplayName("Access token should not be identified as refresh token")
    void accessTokenShouldNotBeRefreshToken() {
        String accessToken = tokenProvider.generateAccessToken(testUser);

        assertThat(tokenProvider.isRefreshToken(accessToken)).isFalse();
    }
}
