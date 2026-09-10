package com.cinebook.service;

import com.cinebook.dto.request.ChangePasswordRequest;
import com.cinebook.dto.request.UpdateProfileRequest;
import com.cinebook.dto.response.UserProfileResponse;
import com.cinebook.entity.User;
import com.cinebook.enums.UserStatus;
import com.cinebook.exception.BadRequestException;
import com.cinebook.exception.ConflictException;
import com.cinebook.mapper.UserMapper;
import com.cinebook.repository.UserRepository;
import com.cinebook.security.UserDetailsImpl;
import com.cinebook.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.cinebook.repository.RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Spy
    private UserMapper userMapper = new UserMapper();

    @InjectMocks
    private UserServiceImpl userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId("user-123");
        sampleUser.setEmail("user@example.com");
        sampleUser.setFullName("Original Name");
        sampleUser.setPasswordHash("encoded_current_pwd");
        sampleUser.setPhone("0901234567");
        sampleUser.setStatus(UserStatus.ACTIVE);

        UserDetailsImpl userDetails = UserDetailsImpl.builder()
                .id("user-123")
                .email("user@example.com")
                .fullName("Original Name")
                .password("encoded_current_pwd")
                .status(UserStatus.ACTIVE)
                .isDeleted(false)
                .authorities(List.of())
                .build();

        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentProfile_Success() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        UserProfileResponse response = userService.getCurrentProfile();

        assertNotNull(response);
        assertEquals("user-123", response.getId());
        assertEquals("user@example.com", response.getEmail());
    }

    @Test
    void updateCurrentProfile_Success() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0909999999")
                .avatarUrl("http://example.com/avatar.jpg")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhone("0909999999")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.updateCurrentProfile(request);

        assertNotNull(response);
        assertEquals("Updated Name", response.getFullName());
        assertEquals("0909999999", response.getPhone());
        assertEquals("http://example.com/avatar.jpg", response.getAvatarUrl());
    }

    @Test
    void updateCurrentProfile_DuplicatePhone_ThrowsConflict() {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Updated Name")
                .phone("0909999999")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(userRepository.existsByPhone("0909999999")).thenReturn(true);

        assertThrows(ConflictException.class, () -> userService.updateCurrentProfile(request));
    }

    @Test
    void changePassword_Success() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("old_password")
                .newPassword("new_password")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("old_password", "encoded_current_pwd")).thenReturn(true);
        when(passwordEncoder.matches("new_password", "encoded_current_pwd")).thenReturn(false);
        when(passwordEncoder.encode("new_password")).thenReturn("encoded_new_pwd");

        userService.changePassword(request);

        assertEquals("encoded_new_pwd", sampleUser.getPasswordHash());
        verify(userRepository).save(sampleUser);
    }

    @Test
    void changePassword_WrongCurrentPassword_ThrowsBadRequest() {
        ChangePasswordRequest request = ChangePasswordRequest.builder()
                .currentPassword("wrong_password")
                .newPassword("new_password")
                .build();

        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));
        when(passwordEncoder.matches("wrong_password", "encoded_current_pwd")).thenReturn(false);

        assertThrows(BadRequestException.class, () -> userService.changePassword(request));
    }

    @Test
    void adminUpdateUser_Success() {
        User target = new User();
        target.setId("user-456");
        target.setEmail("target@example.com");
        target.setFullName("Target User");
        target.setPhone("0900000002");
        target.setPasswordHash("hashed_secret");
        target.setStatus(UserStatus.ACTIVE);

        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("Updated Target")
                .status(UserStatus.BLOCKED)
                .roles(java.util.Set.of("CUSTOMER"))
                .build();

        com.cinebook.entity.Role customerRole = new com.cinebook.entity.Role();
        customerRole.setId("role-cust");
        customerRole.setName("CUSTOMER");

        when(userRepository.findById("user-456")).thenReturn(Optional.of(target));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.adminUpdateUser("user-456", request);

        assertNotNull(response);
        assertEquals("Updated Target", response.getFullName());
        assertEquals("0900000002", response.getPhone());
        assertEquals("target@example.com", response.getEmail());
        assertEquals("hashed_secret", target.getPasswordHash());
        assertEquals(UserStatus.BLOCKED, response.getStatus());
        assertTrue(response.getRoles().contains("CUSTOMER"));
    }

    @Test
    void adminUpdateUser_PreservesPrivateFields_EmailPhonePassword() {
        User target = new User();
        target.setId("user-456");
        target.setEmail("private@example.com");
        target.setPhone("0912345678");
        target.setPasswordHash("super_secret_hash");
        target.setFullName("Original Target");
        target.setStatus(UserStatus.ACTIVE);

        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("Renamed By Admin")
                .status(UserStatus.ACTIVE)
                .roles(java.util.Set.of("CUSTOMER"))
                .build();

        com.cinebook.entity.Role customerRole = new com.cinebook.entity.Role();
        customerRole.setId("role-cust");
        customerRole.setName("CUSTOMER");

        when(userRepository.findById("user-456")).thenReturn(Optional.of(target));
        when(roleRepository.findByName("CUSTOMER")).thenReturn(Optional.of(customerRole));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserProfileResponse response = userService.adminUpdateUser("user-456", request);

        // Verify that Admin only modified fullName, roles, status
        assertEquals("Renamed By Admin", response.getFullName());
        assertEquals(UserStatus.ACTIVE, response.getStatus());

        // Verify that private fields remain untouched
        assertEquals("private@example.com", target.getEmail());
        assertEquals("0912345678", target.getPhone());
        assertEquals("super_secret_hash", target.getPasswordHash());
        assertEquals("private@example.com", response.getEmail());
        assertEquals("0912345678", response.getPhone());
    }

    @Test
    void adminUpdateUser_SelfDisable_ThrowsBadRequest() {
        // Authenticated user in setUp() is "user-123"
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("My Name")
                .status(UserStatus.BLOCKED)
                .roles(java.util.Set.of("ADMIN"))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.adminUpdateUser("user-123", request));
        assertTrue(ex.getMessage().contains("cannot disable their own account"));
    }

    @Test
    void adminUpdateUser_SelfDemotion_ThrowsBadRequest() {
        // Authenticated user in setUp() is "user-123"
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("My Name")
                .status(UserStatus.ACTIVE)
                .roles(java.util.Set.of("CUSTOMER")) // does not contain ADMIN
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.adminUpdateUser("user-123", request));
        assertTrue(ex.getMessage().contains("cannot remove the ADMIN role"));
    }

    @Test
    void adminUpdateUser_LastAdminProtection_ThrowsBadRequest() {
        User adminB = new User();
        adminB.setId("admin-b");
        adminB.setStatus(UserStatus.ACTIVE);

        com.cinebook.entity.Role adminRole = new com.cinebook.entity.Role();
        adminRole.setId("role-admin");
        adminRole.setName("ADMIN");

        com.cinebook.entity.UserRole ur = new com.cinebook.entity.UserRole();
        ur.setId(new com.cinebook.entity.UserRoleId("admin-b", "role-admin"));
        ur.setUser(adminB);
        ur.setRole(adminRole);
        adminB.getUserRoles().add(ur);

        when(userRepository.findById("admin-b")).thenReturn(Optional.of(adminB));
        when(roleRepository.findByNameWithLock("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.countActiveAdmins()).thenReturn(1L); // Only 1 active admin!

        // Attempting to demote adminB to CUSTOMER
        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("Admin B")
                .status(UserStatus.ACTIVE)
                .roles(java.util.Set.of("CUSTOMER"))
                .build();

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.adminUpdateUser("admin-b", request));
        assertTrue(ex.getMessage().contains("last active administrator"));
        verify(roleRepository).findByNameWithLock("ADMIN");
    }

    @Test
    void adminUpdateUser_InvalidRole_ThrowsBadRequest() {
        when(userRepository.findById("user-456")).thenReturn(Optional.of(new User()));

        com.cinebook.dto.request.AdminUpdateUserRequest request = com.cinebook.dto.request.AdminUpdateUserRequest.builder()
                .fullName("Target")
                .status(UserStatus.ACTIVE)
                .roles(java.util.Set.of("SUPERUSER_HACK"))
                .build();

        assertThrows(BadRequestException.class, () -> userService.adminUpdateUser("user-456", request));
    }

    @Test
    void updateUserStatus_SelfDisable_ThrowsBadRequest() {
        when(userRepository.findById("user-123")).thenReturn(Optional.of(sampleUser));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.updateUserStatus("user-123", UserStatus.BLOCKED));
        assertTrue(ex.getMessage().contains("cannot disable their own account"));
    }

    @Test
    void updateUserStatus_LastAdminProtection_ThrowsBadRequest() {
        User adminB = new User();
        adminB.setId("admin-b");
        adminB.setStatus(UserStatus.ACTIVE);

        com.cinebook.entity.Role adminRole = new com.cinebook.entity.Role();
        adminRole.setId("role-admin");
        adminRole.setName("ADMIN");

        com.cinebook.entity.UserRole ur = new com.cinebook.entity.UserRole();
        ur.setUser(adminB);
        ur.setRole(adminRole);
        adminB.getUserRoles().add(ur);

        when(userRepository.findById("admin-b")).thenReturn(Optional.of(adminB));
        when(roleRepository.findByNameWithLock("ADMIN")).thenReturn(Optional.of(adminRole));
        when(userRepository.countActiveAdmins()).thenReturn(1L);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> userService.updateUserStatus("admin-b", UserStatus.BLOCKED));
        assertTrue(ex.getMessage().contains("last active administrator"));
    }
}

