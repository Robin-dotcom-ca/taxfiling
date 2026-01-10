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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    /**
     * Register a new user (always as TAXPAYER role).
     */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            throw ApiException.conflict("EMAIL_EXISTS", "Email is already registered");
        }

        // Create new user
        User user = User.builder()
                .email(request.getEmail().toLowerCase())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.TAXPAYER)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .build();

        user = userRepository.save(user);
        log.info("New user registered: {}", user.getEmail());

        // Generate tokens
        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String accessToken = tokenProvider.generateAccessToken(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Login with email and password.
     */
    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        request.getEmail().toLowerCase(),
                        request.getPassword()
                )
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        String accessToken = tokenProvider.generateAccessToken(userPrincipal);
        String refreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        log.info("User logged in: {}", user.getEmail());

        return buildAuthResponse(user, accessToken, refreshToken);
    }

    /**
     * Refresh access token using refresh token.
     */
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        if (!tokenProvider.validateToken(refreshToken)) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Invalid refresh token");
        }

        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Token is not a refresh token");
        }

        var userId = tokenProvider.getUserIdFromToken(refreshToken);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        UserPrincipal userPrincipal = UserPrincipal.create(user);
        String newAccessToken = tokenProvider.generateAccessToken(userPrincipal);
        String newRefreshToken = tokenProvider.generateRefreshToken(userPrincipal);

        return buildAuthResponse(user, newAccessToken, newRefreshToken);
    }

    private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getExpirationInSeconds())
                .user(AuthResponse.UserInfo.builder()
                        .id(user.getId())
                        .role(user.getRole())
                        .build())
                .build();
    }
}
