package com.learniq.auth.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@DisplayName("SuperAdminBootstrap (first platform user) unit tests")
@ExtendWith(MockitoExtension.class)
class SuperAdminBootstrapTest {

    @Mock private JdbcOperations jdbcTemplate;
    @Mock private PasswordEncoder passwordEncoder;

    private SuperAdminBootstrap newBootstrap(boolean enabled, String username, String password) throws Exception {
        SuperAdminBootstrap bootstrap = new SuperAdminBootstrap(jdbcTemplate, passwordEncoder);
        java.lang.reflect.Field enabledField = SuperAdminBootstrap.class.getDeclaredField("enabled");
        enabledField.setAccessible(true);
        enabledField.set(bootstrap, enabled);

        java.lang.reflect.Field usernameField = SuperAdminBootstrap.class.getDeclaredField("username");
        usernameField.setAccessible(true);
        usernameField.set(bootstrap, username);

        java.lang.reflect.Field passwordField = SuperAdminBootstrap.class.getDeclaredField("configuredPassword");
        passwordField.setAccessible(true);
        passwordField.set(bootstrap, password);
        return bootstrap;
    }

    @Test
    @DisplayName("creates the first SUPER_ADMIN when global_users is empty")
    void createsSuperAdminWhenTableEmpty() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.global_users", Long.class)).thenReturn(0L);
        when(passwordEncoder.encode("Secret#123")).thenReturn("bcrypt-hash");

        newBootstrap(true, "platform_admin", "Secret#123").bootstrap();

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO public.global_users"), captor.capture());
        Object[] args = captor.getValue();
        assertThat(args[1]).isEqualTo("platform_admin");
        assertThat(args[2]).isEqualTo("bcrypt-hash");
        assertThat(args[3]).isEqualTo("platform_admin@superadmin.learniq.local");
        assertThat(args[4]).isEqualTo("SUPER_ADMIN");
        assertThat(UUID.fromString(args[0].toString())).isNotNull();
    }

    @Test
    @DisplayName("skips creation when platform users already exist")
    void skipsWhenTableNotEmpty() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.global_users", Long.class)).thenReturn(1L);

        newBootstrap(true, "superadmin", "whatever").bootstrap();

        verify(jdbcTemplate, never()).update(anyString(), any(Object[].class));
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("does nothing when disabled by configuration")
    void disabledDoesNothing() throws Exception {
        newBootstrap(false, "superadmin", "pw").bootstrap();

        verifyNoInteractions(jdbcTemplate);
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    @DisplayName("generated password is strong enough and hashed")
    void generatesAndEncodesPassword() throws Exception {
        when(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public.global_users", Long.class)).thenReturn(0L);
        when(passwordEncoder.encode(anyString())).thenReturn("generated-hash");

        newBootstrap(true, "superadmin", "  ").bootstrap();

        ArgumentCaptor<String> passwordCaptor = ArgumentCaptor.forClass(String.class);
        verify(passwordEncoder).encode(passwordCaptor.capture());
        assertThat(passwordCaptor.getValue()).hasSize(12);

        ArgumentCaptor<Object[]> captor = ArgumentCaptor.forClass(Object[].class);
        verify(jdbcTemplate).update(contains("INSERT INTO public.global_users"), captor.capture());
        Object[] args = captor.getValue();
        assertThat(args[1]).isEqualTo("superadmin");
        assertThat(args[2]).isEqualTo("generated-hash");
        assertThat(args[4]).isEqualTo("SUPER_ADMIN");
    }
}
