package com.loginpage.API.repository;
import com.loginpage.API.model.PasswordResetToken;
import com.loginpage.API.model.User;
import org.springframework.data.jpa.repository.JpaRepository;


import java.util.Optional;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {
    Optional<PasswordResetToken> findByToken(String token);
    Optional<PasswordResetToken> findByUser(User user); 
}
