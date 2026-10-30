package com.laexclusiva.model;

import java.time.LocalDateTime;

/**
 * Representa la sesión activa de un usuario en el sistema.
 * 
 * @author Daniel Arrieta
 */
public class UserSession {

    private final UserAccount userAccount;
    private final LocalDateTime loginTime;

    public UserSession(UserAccount userAccount) {
        if (userAccount == null) {
            throw new IllegalArgumentException("La cuenta de usuario no puede ser nula.");
        }
        this.userAccount = userAccount;
        this.loginTime = LocalDateTime.now();
    }

    public UserAccount getUserAccount() {
        return userAccount;
    }

    public LocalDateTime getLoginTime() {
        return loginTime;
    }
}
