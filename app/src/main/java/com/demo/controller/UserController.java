package com.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.util.HashMap;

@RestController
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @GetMapping("/")
    public Map<String, Object> index(@AuthenticationPrincipal OAuth2User principal) {
        
        // Add user context to MDC for better logging
        MDC.put("userId", principal.getAttribute("sub"));
        MDC.put("username", principal.getAttribute("preferred_username"));
        
        logger.info("USER_ACCESS: Processing user request");

        try {
            Map<String, Object> response = new HashMap<>();
            response.put("userId", principal.getAttribute("sub"));
            response.put("username", principal.getAttribute("preferred_username"));
            response.put("email", principal.getAttribute("email") != null ? principal.getAttribute("email") : "no email");
            response.put("auth_time", principal.getAttribute("auth_time"));
            response.put("demo-attr", principal.getAttribute("demo-attr"));
            response.put("clientId", principal.getAttribute("azp"));
            response.put("issuer", principal.getAttribute("iss"));

            logger.info("USER_DATA_RETURNED: Successfully returned user information");
            return response;
            
        } catch (Exception e) {
            logger.error("ERROR_PROCESSING_USER_REQUEST: {}", e.getMessage(), e);
            throw e;
        } finally {
            MDC.clear();
        }
    }

    @PostMapping("/webhook/login")
    public Map<String, Object> loginWebhook(@RequestBody Map<String, Object> payload) {
        MDC.put("webhookEvent", "LOGIN_SUCCESS");
        
        try {
            logger.info("WEBHOOK_RECEIVED: {}", payload);
            
            // Process webhook payload
            String userId = (String) payload.get("userId");
            String username = (String) payload.get("username");
            String clientId = (String) payload.get("clientId");
            
            logger.info("WEBHOOK_PROCESSED: userId={}, username={}, clientId={}", userId, username, clientId);
            
            return Map.of(
                "status", "success",
                "message", "Login webhook processed",
                "timestamp", System.currentTimeMillis()
            );
            
        } catch (Exception e) {
            logger.error("WEBHOOK_ERROR: {}", e.getMessage(), e);
            return Map.of(
                "status", "error",
                "message", "Failed to process webhook",
                "error", e.getMessage()
            );
        } finally {
            MDC.clear();
        }
    }
}
