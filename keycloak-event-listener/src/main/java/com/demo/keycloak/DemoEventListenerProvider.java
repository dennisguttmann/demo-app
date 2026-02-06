package com.demo.keycloak;

import org.jboss.logging.Logger;
import org.keycloak.events.Event;
import org.keycloak.events.EventListenerProvider;
import org.keycloak.events.EventType;
import org.keycloak.events.admin.AdminEvent;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.UserModel;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.ContentType;

import java.util.HashMap;
import java.util.Map;

public class DemoEventListenerProvider implements EventListenerProvider {

    private static final Logger logger = Logger.getLogger(DemoEventListenerProvider.class);
    private static final String WEBHOOK_URL = "http://java-app:8088/webhook/login";
    
    private final KeycloakSession session;
    private final CloseableHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public DemoEventListenerProvider(KeycloakSession session) {
        this.session = session;
        this.httpClient = HttpClients.createDefault();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public void onEvent(Event event) {
        if (event.getType() == EventType.LOGIN) {
            handleLoginEvent(event);
        }
    }

    private void handleLoginEvent(Event event) {
        String userId = event.getUserId();
        String username = event.getDetails().get("username");
        String clientId = event.getClientId();
        String realm = session.getContext().getRealm().getName();

        // Structured logging
        logger.infof("LOGIN_EVENT: {\"userId\":\"%s\",\"username\":\"%s\",\"clientId\":\"%s\",\"realm\":\"%s\",\"timestamp\":\"%d\"}", 
                     userId, username, clientId, realm, System.currentTimeMillis());

        try {
            // Add user attribute
            RealmModel realmModel = session.getContext().getRealm();
            UserModel user = session.users().getUserById(realmModel, userId);
            if (user != null) {
                user.setSingleAttribute("demo-attr", "demo-value");
                logger.infof("USER_ATTRIBUTE_UPDATED: {\"userId\":\"%s\",\"attribute\":\"demo-attr\",\"value\":\"demo-value\"}", userId);
            }

            // Send webhook
            sendWebhook(userId, username, clientId, realm);
            
        } catch (Exception e) {
            logger.errorf("ERROR_PROCESSING_LOGIN_EVENT: {\"userId\":\"%s\",\"error\":\"%s\"}", 
                         userId, e.getMessage());
        }
    }

    private void sendWebhook(String userId, String username, String clientId, String realm) {
        logger.infof("WEBHOOK_INITIATED: {\"userId\":\"%s\",\"username\":\"%s\",\"clientId\":\"%s\",\"realm\":\"%s\",\"url\":\"%s\"}", 
                     userId, username, clientId, realm, WEBHOOK_URL);
        
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "LOGIN_SUCCESS");
            payload.put("userId", userId);
            payload.put("username", username);
            payload.put("clientId", clientId);
            payload.put("realm", realm);
            payload.put("timestamp", System.currentTimeMillis());

            String jsonPayload = objectMapper.writeValueAsString(payload);
            logger.infof("WEBHOOK_PAYLOAD_CREATED: {\"userId\":\"%s\",\"payloadSize\":\"%d\",\"payload\":\"%s\"}", 
                         userId, jsonPayload.length(), jsonPayload);
            
            HttpPost httpPost = new HttpPost(WEBHOOK_URL);
            httpPost.setEntity(new StringEntity(jsonPayload, ContentType.APPLICATION_JSON));
            httpPost.setHeader("Content-Type", "application/json");

            logger.infof("WEBHOOK_REQUEST_SENDING: {\"userId\":\"%s\",\"method\":\"POST\",\"url\":\"%s\"}", 
                         userId, WEBHOOK_URL);

            try (CloseableHttpResponse response = httpClient.execute(httpPost)) {
                int statusCode = response.getCode();
                logger.infof("WEBHOOK_RESPONSE_RECEIVED: {\"userId\":\"%s\",\"statusCode\":\"%d\",\"protocol\":\"%s\"}", 
                             userId, statusCode, response.getVersion());
                
                if (statusCode == 200) {
                    logger.infof("WEBHOOK_SUCCESS: {\"userId\":\"%s\",\"statusCode\":\"%d\"}", userId, statusCode);
                } else {
                    logger.warnf("WEBHOOK_NON_SUCCESS: {\"userId\":\"%s\",\"statusCode\":\"%d\",\"reasonPhrase\":\"%s\"}", 
                                 userId, statusCode, response.getReasonPhrase());
                }
            }
            
        } catch (Exception e) {
            logger.errorf("WEBHOOK_ERROR: {\"userId\":\"%s\",\"errorType\":\"%s\",\"errorMessage\":\"%s\"}", 
                         userId, e.getClass().getSimpleName(), e.getMessage());
        }
    }

    @Override
    public void onEvent(AdminEvent event, boolean includeRepresentation) {
        // we don't care about admin events
    }

    @Override
    public void close() {
        try {
            if (httpClient != null) {
                httpClient.close();
            }
        } catch (Exception e) {
            logger.error("Error closing HTTP client: " + e.getMessage());
        }
    }
}
