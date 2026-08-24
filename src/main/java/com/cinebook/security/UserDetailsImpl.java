package com.cinebook.security;

import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor
public class UserDetailsImpl implements UserDetails {

    private final String id;
    private final String email;
    private final String fullName;

    @JsonIgnore
    private final String password;

    private final UserStatus status;
    private final boolean isDeleted;
    private final Collection<? extends GrantedAuthority> authorities;

    public static UserDetailsImpl build(User user) {
        List<SimpleGrantedAuthority> authorities = (user.getUserRoles() == null)
                ? List.of()
                : user.getUserRoles().stream()
                .filter(ur -> ur.getRole() != null)
                .map(ur -> new SimpleGrantedAuthority("ROLE_" + ur.getRole().getName()))
                .toList();

        return UserDetailsImpl.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .password(user.getPasswordHash())
                .status(user.getStatus())
                .isDeleted(user.getDeletedAt() != null)
                .authorities(authorities)
                .build();
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return status != UserStatus.BLOCKED;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return status == UserStatus.ACTIVE && !isDeleted;
    }
}

