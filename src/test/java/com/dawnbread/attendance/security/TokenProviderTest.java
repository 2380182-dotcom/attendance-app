package com.dawnbread.attendance.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-for-jwt-signing-must-be-long-enough",
        "jwt.expiration-ms=86400000"
})
class TokenProviderTest {

    @Autowired
    private TokenProvider tokenProvider;

    @Test
    void testGenerateTokenWithUsernameAndRole() {
        String username = "AGENT001";
        String role = "AGENT";

        String token = tokenProvider.generateToken(username, role);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);
        assertEquals(username, tokenProvider.getUsernameFromToken(token));
        assertTrue(tokenProvider.validateToken(token));

        Claims claims = tokenProvider.parseToken(token);
        assertEquals(username, claims.getSubject());
        assertEquals(role, claims.get("role", String.class));
    }

    @Test
    void testGenerateAndParseTokenWithId() {
        Long id = 123L;
        String agentId = "AGENT001";
        String role = "AGENT";

        String token = tokenProvider.generateToken(id, agentId, role);

        assertNotNull(token);
        assertEquals(3, token.split("\\.").length);

        Claims claims = tokenProvider.parseToken(token);
        assertEquals(agentId, claims.getSubject());
        assertEquals(role, claims.get("role", String.class));
        assertEquals(id, Long.valueOf(claims.get("id").toString()));
    }

    @Test
    void testValidateTokenWithUsername() {
        String username = "AGENT001";
        String role = "AGENT";
        String token = tokenProvider.generateToken(username, role);

        assertTrue(tokenProvider.validateToken(token, username));
        assertFalse(tokenProvider.validateToken(token, "OTHER_USER"));
    }

    @Test
    void testParseInvalidTokenThrows() {
        assertThrows(JwtException.class, () -> tokenProvider.parseToken("invalid.token.here"));
    }

    @Test
    void testParseTokenWithModifiedSignatureThrows() {
        String token = tokenProvider.generateToken("AGENT001", "AGENT");
        String[] parts = token.split("\\.");
        String tamperedToken = parts[0] + "." + parts[1] + "." + parts[2] + "abc";

        assertThrows(JwtException.class, () -> tokenProvider.parseToken(tamperedToken));
    }
}
