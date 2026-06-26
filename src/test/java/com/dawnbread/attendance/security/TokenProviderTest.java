package com.dawnbread.attendance.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TokenProviderTest {

    private TokenProvider tokenProvider;

    @BeforeEach
    void setUp() {
        tokenProvider = new TokenProvider();
    }

    @Test
    void testGenerateAndParseTokenSuccess() {
        Long id = 123L;
        String agentId = "AGENT001";
        String role = "AGENT";

        String token = tokenProvider.generateToken(id, agentId, role);
        assertNotNull(token);
        assertTrue(token.split("\\.").length == 3);

        TokenProvider.Claims claims = tokenProvider.parseToken(token);
        assertNotNull(claims);
        assertEquals(id, claims.getId());
        assertEquals(agentId, claims.getAgentId());
        assertEquals(role, claims.getRole());
    }

    @Test
    void testParseInvalidTokenReturnsNull() {
        assertNull(tokenProvider.parseToken("invalid.token.here"));
        assertNull(tokenProvider.parseToken(null));
        assertNull(tokenProvider.parseToken(""));
    }

    @Test
    void testParseTokenWithModifiedSignatureReturnsNull() {
        Long id = 123L;
        String agentId = "AGENT001";
        String role = "AGENT";

        String token = tokenProvider.generateToken(id, agentId, role);
        String[] parts = token.split("\\.");
        
        // Modify the signature part slightly
        String tamperedSignature = parts[2] + "abc";
        String tamperedToken = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertNull(tokenProvider.parseToken(tamperedToken));
    }
}
