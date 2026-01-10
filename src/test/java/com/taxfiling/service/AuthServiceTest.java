package com.taxfiling.service;

import com.taxfiling.dto.auth.AuthResponse;
import com.taxfiling.dto.auth.LoginRequest;
import com.taxfiling.dto.auth.RefreshTokenRequest;
import com.taxfiling.dto.auth.RegisterRequest;
import com.taxfiling.exception.ApiException;
import com.taxfiling.model.User;
import com.taxfiling.model.enums.UserRole;
import com.taxfiling.repository.UserRepository;
import com.taxfiling.security.JwtTokenProvider;
import com.taxfiling.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthService authService;

    private User testUser;
    private UserPrincipal testUserPrincipal;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .role(UserRole.TAXPAYER)
                .firstName("Test")
                .lastName("User")
                .build();

        testUser.setId(UUID.randomUUID());
        testUserPrincipal = UserPrincipal.create(testUser);
    }

    @Nested
    @DisplayName("Register Tests")
    class RegisterTests {

        @Test
        @DisplayName("Should register new user successfully")
        void shouldRegisterNewUser() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("newuser@example.com")
                    .password("password123")
                    .firstName("New")
                    .lastName("User")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(UUID.randomUUID());
                return user;
            });
            when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
            when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
            when(tokenProvider.getExpirationInSeconds()).thenReturn(3600L);

            AuthResponse response = authService.register(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
            assertThat(response.getUser().getRole()).isEqualTo(UserRole.TAXPAYER);

            // Verify user was saved with correct data
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            User savedUser = userCaptor.getValue();
            assertThat(savedUser.getEmail()).isEqualTo("newuser@example.com");
            assertThat(savedUser.getRole()).isEqualTo(UserRole.TAXPAYER);
        }

        @Test
        @DisplayName("Should throw exception when email already exists")
        void shouldThrowExceptionWhenEmailExists() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("existing@example.com")
                    .password("password123")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Email is already registered");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Should convert email to lowercase")
        void shouldConvertEmailToLowercase() {
            RegisterRequest request = RegisterRequest.builder()
                    .email("TEST@EXAMPLE.COM")
                    .password("password123")
                    .build();

            when(userRepository.existsByEmail(anyString())).thenReturn(false);
            when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");
            when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
                User user = invocation.getArgument(0);
                user.setId(UUID.randomUUID());
                return user;
            });
            when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("token");
            when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh");
            when(tokenProvider.getExpirationInSeconds()).thenReturn(3600L);

            authService.register(request);

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getEmail()).isEqualTo("test@example.com");
        }
    }

    @Nested
    @DisplayName("Login Tests")
    class LoginTests {

        @Test
        @DisplayName("Should login successfully with valid credentials")
        void shouldLoginSuccessfully() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("password123")
                    .build();

            Authentication authentication = mock(Authentication.class);
            when(authentication.getPrincipal()).thenReturn(testUserPrincipal);
            when(authenticationManager.authenticate(any())).thenReturn(authentication);
            when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(testUser));
            when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("access-token");
            when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("refresh-token");
            when(tokenProvider.getExpirationInSeconds()).thenReturn(3600L);

            AuthResponse response = authService.login(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("access-token");
            assertThat(response.getRefreshToken()).isEqualTo("refresh-token");
        }

        @Test
        @DisplayName("Should throw exception with invalid credentials")
        void shouldThrowExceptionWithInvalidCredentials() {
            LoginRequest request = LoginRequest.builder()
                    .email("test@example.com")
                    .password("wrongpassword")
                    .build();

            when(authenticationManager.authenticate(any()))
                    .thenThrow(new BadCredentialsException("Bad credentials"));

            assertThatThrownBy(() -> authService.login(request))
                    .isInstanceOf(BadCredentialsException.class);
        }
    }

    @Nested
    @DisplayName("Refresh Token Tests")
    class RefreshTokenTests {

        @Test
        @DisplayName("Should refresh token successfully")
        void shouldRefreshTokenSuccessfully() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("valid-refresh-token")
                    .build();

            when(tokenProvider.validateToken(anyString())).thenReturn(true);
            when(tokenProvider.isRefreshToken(anyString())).thenReturn(true);
            when(tokenProvider.getUserIdFromToken(anyString())).thenReturn(testUser.getId());
            when(userRepository.findById(any(UUID.class))).thenReturn(Optional.of(testUser));
            when(tokenProvider.generateAccessToken(any(UserPrincipal.class))).thenReturn("new-access-token");
            when(tokenProvider.generateRefreshToken(any(UserPrincipal.class))).thenReturn("new-refresh-token");
            when(tokenProvider.getExpirationInSeconds()).thenReturn(3600L);

            AuthResponse response = authService.refreshToken(request);

            assertThat(response).isNotNull();
            assertThat(response.getAccessToken()).isEqualTo("new-access-token");
            assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        }

        @Test
        @DisplayName("Should throw exception with invalid refresh token")
        void shouldThrowExceptionWithInvalidRefreshToken() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("invalid-token")
                    .build();

            when(tokenProvider.validateToken(anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Invalid refresh token");
        }

        @Test
        @DisplayName("Should throw exception when using access token as refresh token")
        void shouldThrowExceptionWhenUsingAccessTokenAsRefresh() {
            RefreshTokenRequest request = RefreshTokenRequest.builder()
                    .refreshToken("access-token-not-refresh")
                    .build();

            when(tokenProvider.validateToken(anyString())).thenReturn(true);
            when(tokenProvider.isRefreshToken(anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.refreshToken(request))
                    .isInstanceOf(ApiException.class)
                    .hasMessageContaining("Token is not a refresh token");
        }
    }
}
