package com.loginpage.API.service;

import com.loginpage.API.model.AuditLog;
import com.loginpage.API.model.User;
import com.loginpage.API.repository.AuditLogRepository;
import com.loginpage.API.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@Service
public class AuditLogService {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Transactional
    public void logAction(String username, String action, String details, HttpServletRequest request) {
        try {
            Optional<User> userOpt = userRepository.findByUsername(username);
            Long userId = userOpt.map(User::getId).orElse(null);
            
            String ipAddress = getClientIpAddress(request);
            
            AuditLog auditLog = new AuditLog(userId, username, action, details, ipAddress);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            // Log error but don't break the main flow
            System.err.println("Failed to create audit log: " + e.getMessage());
        }
    }

    @Transactional
    public void logAction(Long userId, String username, String action, String details, HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            AuditLog auditLog = new AuditLog(userId, username, action, details, ipAddress);
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            System.err.println("Failed to create audit log: " + e.getMessage());
        }
    }

    public List<AuditLog> getAllLogs() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc();
    }

    public List<AuditLog> getLogsByUsername(String username) {
        return auditLogRepository.findByUsernameOrderByCreatedAtDesc(username);
    }

    public List<AuditLog> getLogsByAction(String action) {
        return auditLogRepository.findByActionOrderByCreatedAtDesc(action);
    }

    private String getClientIpAddress(HttpServletRequest request) {
        if (request == null) return "Unknown";
        
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}

