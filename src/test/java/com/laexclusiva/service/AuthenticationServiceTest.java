package com.laexclusiva.service;

import com.laexclusiva.model.UserAccount;
import com.laexclusiva.model.UserRole;
import com.laexclusiva.repository.InMemoryUserRepository;
import com.laexclusiva.security.PasswordHasher;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    @Test
    public void testAuthenticationSuccess() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        String hashedPassword = PasswordHasher.hash("admin123");
        
        repository.save(new UserAccount(1L, "admin", hashedPassword, UserRole.ADMINISTRATOR, null));
        AuthenticationService authService = new AuthenticationService(repository);

        // Usamos .orElse(null) porque authenticate retorna un Optional<UserAccount>
        UserAccount loggedInUser = authService.authenticate("admin", "admin123").orElse(null);
        
        assertNotNull(loggedInUser, "El usuario debería autenticarse correctamente con credenciales válidas");
        assertEquals("admin", loggedInUser.getUsername());
    }

    @Test
    public void testAuthenticationFailureWrongPassword() {
        InMemoryUserRepository repository = new InMemoryUserRepository();
        String hashedPassword = PasswordHasher.hash("admin123");
        
        repository.save(new UserAccount(1L, "admin", hashedPassword, UserRole.ADMINISTRATOR, null));
        AuthenticationService authService = new AuthenticationService(repository);

        UserAccount loggedInUser = authService.authenticate("admin", "wrongPassword").orElse(null);
        
        assertNull(loggedInUser, "La autenticación debe fallar si la contraseña es incorrecta");
    }
}