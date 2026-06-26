package com.dawnbread.attendance.security;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class TokenProvider {

    private static final String SECRET_KEY = "AntigravitySecretKeyForJWTAuthSuperSecureAndLongEnoughToAvoidErrors";
    private static final long EXPIRATION_TIME_MS = 864000000L; // 10 days
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    public String generateToken(Long id, String agentId, String role) {
        try {
            long exp = System.currentTimeMillis() + EXPIRATION_TIME_MS;
            String payloadJson = "{\"id\":" + id + ",\"agentId\":\"" + agentId + "\",\"role\":\"" + role + "\",\"exp\":" + exp + "}";

            String headerBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
            String payloadBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String content = headerBase64 + "." + payloadBase64;
            String signature = sign(content, SECRET_KEY);

            return content + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate JWT token", e);
        }
    }

    public Claims parseToken(String token) {
        try {
            if (token == null) {
                return null;
            }
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String headerBase64 = parts[0];
            String payloadBase64 = parts[1];
            String signature = parts[2];

            String content = headerBase64 + "." + payloadBase64;
            String expectedSignature = sign(content, SECRET_KEY);

            if (!expectedSignature.equals(signature)) {
                return null;
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(payloadBase64), StandardCharsets.UTF_8);
            Map<String, Object> claimsMap = parseJson(payloadJson);

            if (!claimsMap.containsKey("exp") || !claimsMap.containsKey("id") || !claimsMap.containsKey("agentId") || !claimsMap.containsKey("role")) {
                return null;
            }

            long exp = ((Number) claimsMap.get("exp")).longValue();
            if (System.currentTimeMillis() > exp) {
                return null; // Expired
            }

            Long id = ((Number) claimsMap.get("id")).longValue();
            String agentId = (String) claimsMap.get("agentId");
            String role = (String) claimsMap.get("role");

            return new Claims(id, agentId, role);
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data, String secret) throws Exception {
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        sha256Hmac.init(secretKey);
        byte[] hash = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
    }

    private Map<String, Object> parseJson(String json) {
        Map<String, Object> map = new HashMap<>();
        json = json.trim();
        if (json.startsWith("{") && json.endsWith("}")) {
            json = json.substring(1, json.length() - 1);
        }
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.split(":", 2);
            if (keyValue.length == 2) {
                String key = keyValue[0].trim().replace("\"", "");
                String value = keyValue[1].trim();
                if (value.startsWith("\"") && value.endsWith("\"")) {
                    map.put(key, value.substring(1, value.length() - 1));
                } else {
                    try {
                        map.put(key, Long.parseLong(value));
                    } catch (NumberFormatException e) {
                        try {
                            map.put(key, Double.parseDouble(value));
                        } catch (NumberFormatException ex) {
                            map.put(key, value);
                        }
                    }
                }
            }
        }
        return map;
    }

    public static class Claims {
        private final Long id;
        private final String agentId;
        private final String role;

        public Claims(Long id, String agentId, String role) {
            this.id = id;
            this.agentId = agentId;
            this.role = role;
        }

        public Long getId() { return id; }
        public String getAgentId() { return agentId; }
        public String getRole() { return role; }
    }
}
