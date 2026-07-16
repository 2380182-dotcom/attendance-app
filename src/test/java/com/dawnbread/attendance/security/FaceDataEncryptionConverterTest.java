package com.dawnbread.attendance.security;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit tests, no Spring context — FaceDataEncryptionKeyHolder's
 * constructor just populates a static map, so it can be primed directly to
 * exercise FaceDataEncryptionConverter's crypto logic in isolation.
 *
 * IMPORTANT: that static map is process-wide, and Spring Test's context
 * caching means most @SpringBootTest classes in this suite share ONE
 * long-lived context — so FaceDataEncryptionKeyHolder's constructor (which
 * populates the map from application.properties) only runs ONCE per test
 * JVM, not once per test class. This class must therefore leave the map in
 * EXACTLY the state application.properties configured it to before it
 * finishes, or any @SpringBootTest class that runs afterward in the same
 * JVM would fail to decrypt data encrypted earlier under that real key.
 */
class FaceDataEncryptionConverterTest {

    // Must match src/test/resources/application.properties' face.data.encryption.key
    // exactly — this is what gets restored in @AfterAll.
    private static final String SHARED_TEST_KEY_BASE64 = "R2AzY/UTQZTd6LHdIgV2EcbYkwj9sXXnPon4FsKs/G4=";

    private static final FaceDataEncryptionConverter CONVERTER = new FaceDataEncryptionConverter();

    @BeforeAll
    static void primeKey() {
        new FaceDataEncryptionKeyHolder(SHARED_TEST_KEY_BASE64);
    }

    @AfterAll
    static void restoreSharedTestKey() {
        new FaceDataEncryptionKeyHolder(SHARED_TEST_KEY_BASE64);
    }

    @Test
    void roundTripsPlaintextThroughEncryptAndDecrypt() {
        String original = "the-quick-brown-fox-jumped-384-floats-worth-of-base64-data";
        String stored = CONVERTER.convertToDatabaseColumn(original);
        String recovered = CONVERTER.convertToEntityAttribute(stored);
        assertEquals(original, recovered);
    }

    @Test
    void storedValueIsNotThePlaintext() {
        String original = "raw-biometric-embedding-value-that-must-never-appear-verbatim";
        String stored = CONVERTER.convertToDatabaseColumn(original);
        assertNotEquals(original, stored);
        assertFalse(stored.contains(original), "The stored value must not contain the plaintext as a substring");
    }

    @Test
    void storedValueCarriesTheVersionPrefix() {
        String stored = CONVERTER.convertToDatabaseColumn("anything");
        assertTrue(stored.startsWith("ENCv1:"), "Stored value must be self-describing with a key-version prefix: " + stored);
    }

    @Test
    void twoEncryptionsOfTheSameValueProduceDifferentCiphertext() {
        // A fresh random nonce every call — proves nonces aren't being reused,
        // which would be a real cryptographic weakness for GCM.
        String a = CONVERTER.convertToDatabaseColumn("same-input");
        String b = CONVERTER.convertToDatabaseColumn("same-input");
        assertNotEquals(a, b);
    }

    @Test
    void legacyPlaintextWithNoPrefixNowFailsLoudly() {
        // Dual-read support was removed once production was confirmed to have
        // zero legacy-format rows — a value reaching here now means a
        // regression, so it must fail loudly, not silently pass through.
        String legacyValue = "cGxhaW4tYmFzZTY0LWZsb2F0LWFycmF5LXdpdGgtbm8tcHJlZml4";
        assertThrows(IllegalStateException.class, () -> CONVERTER.convertToEntityAttribute(legacyValue));
    }

    @Test
    void nullPassesThroughBothDirections() {
        assertEquals(null, CONVERTER.convertToDatabaseColumn(null));
        assertEquals(null, CONVERTER.convertToEntityAttribute(null));
    }

    @Test
    void noKeyConfiguredFallsBackToPassThroughOnWrite() {
        new FaceDataEncryptionKeyHolder(""); // simulates an unconfigured instance
        try {
            String value = "unencrypted-because-no-key-is-set";
            assertEquals(value, CONVERTER.convertToDatabaseColumn(value),
                    "With no key configured, writes must pass through unchanged rather than fail");
        } finally {
            // Restore the real shared test key immediately — do not leave the
            // process-wide static state cleared for whatever test runs next.
            primeKey();
        }
    }
}
