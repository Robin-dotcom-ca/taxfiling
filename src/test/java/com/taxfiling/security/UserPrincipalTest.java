package com.taxfiling.security;

import com.taxfiling.model.User;
import com.taxfiling.model.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("UserPrincipal Tests")
class UserPrincipalTest {

    @Test
    @DisplayName("Should create UserPrincipal from User entity")
    void shouldCreateFromUserEntity() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .role(UserRole.TAXPAYER)
                .firstName("Test")
                .lastName("User")
                .build();
        user.setId(UUID.randomUUID());

        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.getId()).isEqualTo(user.getId());
        assertThat(principal.getEmail()).isEqualTo(user.getEmail());
        assertThat(principal.getPassword()).isEqualTo(user.getPasswordHash());
        assertThat(principal.getRole()).isEqualTo(UserRole.TAXPAYER);
        assertThat(principal.getUsername()).isEqualTo(user.getEmail());
    }

    @Test
    @DisplayName("Should have correct authority for taxpayer")
    void shouldHaveCorrectAuthorityForTaxpayer() {
        User user = User.builder()
                .email("taxpayer@example.com")
                .passwordHash("hashedPassword")
                .role(UserRole.TAXPAYER)
                .build();

        user.setId(UUID.randomUUID());
        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.getAuthorities()).hasSize(1);
        assertThat(principal.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_TAXPAYER");
        assertThat(principal.isTaxpayer()).isTrue();
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("Should have correct authority for admin")
    void shouldHaveCorrectAuthorityForAdmin() {
        User user = User.builder()
                .email("admin@example.com")
                .passwordHash("hashedPassword")
                .role(UserRole.ADMIN)
                .build();

        user.setId(UUID.randomUUID());
        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.getAuthorities()).hasSize(1);
        assertThat(principal.getAuthorities().iterator().next().getAuthority())
                .isEqualTo("ROLE_ADMIN");
        assertThat(principal.isAdmin()).isTrue();
        assertThat(principal.isTaxpayer()).isFalse();
    }

    @Test
    @DisplayName("Should return true for all account status methods")
    void shouldReturnTrueForAccountStatus() {
        User user = User.builder()
                .email("test@example.com")
                .passwordHash("hashedPassword")
                .role(UserRole.TAXPAYER)
                .build();

        user.setId(UUID.randomUUID());
        UserPrincipal principal = UserPrincipal.create(user);

        assertThat(principal.isAccountNonExpired()).isTrue();
        assertThat(principal.isAccountNonLocked()).isTrue();
        assertThat(principal.isCredentialsNonExpired()).isTrue();
        assertThat(principal.isEnabled()).isTrue();
    }
}
