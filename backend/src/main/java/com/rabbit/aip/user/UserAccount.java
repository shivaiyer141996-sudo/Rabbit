package com.rabbit.aip.user;

import com.rabbit.aip.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount extends BaseEntity {

    @Column(nullable = false, unique = true, length = 320)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AccountStatus status;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "first_login", nullable = false)
    private boolean firstLogin;

    protected UserAccount() {
    }

    public UserAccount(
            String email,
            String passwordHash,
            String firstName,
            String lastName,
            AccountStatus status,
            boolean firstLogin
    ) {
        this.email = email.trim().toLowerCase();
        this.passwordHash = passwordHash;
        this.firstName = firstName.trim();
        this.lastName = lastName.trim();
        this.status = status;
        this.firstLogin = firstLogin;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Instant getLockedUntil() {
        return lockedUntil;
    }

    public boolean isFirstLogin() {
        return firstLogin;
    }

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public void recordFailedAttempt(
            int maximumAttempts,
            Duration lockDuration,
            Instant now
    ) {
        if (lockedUntil != null && !lockedUntil.isAfter(now)) {
            clearFailedAttempts();
        }
        failedAttempts += 1;
        if (failedAttempts >= maximumAttempts) {
            lockedUntil = now.plus(lockDuration);
        }
    }

    public void clearFailedAttempts() {
        failedAttempts = 0;
        lockedUntil = null;
    }

    public void activate(String encodedPassword) {
        passwordHash = encodedPassword;
        status = AccountStatus.ACTIVE;
        firstLogin = true;
        clearFailedAttempts();
    }

    public boolean consumeFirstLogin() {
        boolean first = firstLogin;
        firstLogin = false;
        return first;
    }

    public void setStatus(AccountStatus status) {
        this.status = status;
    }
}
