package com.loginpage.API.controller;

import com.loginpage.API.model.AuditLog;
import com.loginpage.API.service.AuditLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
@RequestMapping("/audit-log")
public class AuditLogController {

    @Autowired
    private AuditLogService auditLogService;

    @GetMapping
    public String showAuditLog(@RequestParam(required = false) String username,
                               @RequestParam(required = false) String action,
                               Model model) {
        List<AuditLog> logs;
        
        if (username != null && !username.isEmpty()) {
            logs = auditLogService.getLogsByUsername(username);
        } else if (action != null && !action.isEmpty()) {
            logs = auditLogService.getLogsByAction(action);
        } else {
            logs = auditLogService.getAllLogs();
        }
        
        model.addAttribute("auditLogs", logs);
        model.addAttribute("filterUsername", username);
        model.addAttribute("filterAction", action);
        return "audit-log";
    }
}

