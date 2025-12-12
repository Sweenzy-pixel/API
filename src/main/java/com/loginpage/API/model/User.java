package com.loginpage.API.model;

import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    // ✅ Two-Factor Authentication (2FA) fields
    @Column(name = "two_factor_enabled", nullable = false)
private boolean twoFactorEnabled = false; 

    @Column(name = "secret_key")
    private String secretKey;

    // ---------------- Getters and Setters ----------------

    public Long getId() { 
        return id; 
    }

    public void setId(Long id) { 
        this.id = id; 
    }

    public String getUsername() { 
        return username; 
    }

    public void setUsername(String username) { 
        this.username = username; 
    }

    public String getPassword() { 
        return password; 
    }

    public void setPassword(String password) { 
        this.password = password; 
    }

    public String getEmail() { 
        return email; 
    }

    public void setEmail(String email) { 
        this.email = email; 
    }

    public boolean isTwoFactorEnabled() { 
        return twoFactorEnabled; 
    }

    public void setTwoFactorEnabled(boolean twoFactorEnabled) { 
        this.twoFactorEnabled = twoFactorEnabled; 
    }

    public String getSecretKey() { 
        return secretKey; 
    }

    public void setSecretKey(String secretKey) { 
        this.secretKey = secretKey; 
    }
}
