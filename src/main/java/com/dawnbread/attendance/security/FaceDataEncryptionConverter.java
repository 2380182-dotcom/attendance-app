package com.dawnbread.attendance.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM field encryption for Agent.faceEmbedding / faceTemplate — see
 * the approved design doc (biometric-encryption-design.html) for the full
 * rationale. Not @Component / not autoApply: applied explicitly via
 * @Convert on exactly the two fields it's meant for, nothing else.
 *
 * Stored format: "ENCv<version>:" + base64(12-byte GCM nonce + ciphertext + tag).
 * The version prefix is what makes key rotation and this migration possible —
 * a stored value announces which key decrypts it, so old and new keys (or,
 * during migration, encrypted and legacy-plaintext rows) can coexist.
 *
 * Dual-read: a stored value with no "ENCv" prefix is legacy plaintext (the
 * only format that ever existed before this feature) and is returned as-is
 * — this is what makes the Stage 2 deploy non-breaking with zero downtime.
 * Every write always encrypts, once a key is configured.
 */
@Converter
public class FaceDataEncryptionConverter implements AttributeConverter<String, String> {

    private static final String PREFIX = "ENCv";
    private static final int CURRENT_VERSION = 1;
    private static final int GCM_NONCE_LENGTH = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        SecretKeySpec key = FaceDataEncryptionKeyHolder.getKey(CURRENT_VERSION);
        if (key == null) {
            // No key configured on this instance — pass through unchanged.
            return plaintext;
        }
        try {
            byte[] nonce = new byte[GCM_NONCE_LENGTH];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[nonce.length + ciphertext.length];
            System.arraycopy(nonce, 0, combined, 0, nonce.length);
            System.arraycopy(ciphertext, 0, combined, nonce.length, ciphertext.length);

            return PREFIX + CURRENT_VERSION + ":" + Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to encrypt face data field", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String stored) {
        if (stored == null) {
            return null;
        }
        if (!stored.startsWith(PREFIX)) {
            // DIAG(2026-07-25): legacy-plaintext dual-read fallback, kept only
            // for the Stage 3 backfill's bake-in window (encryption design
            // doc, Stage 4). Once the backfill has been stable for a couple
            // of weeks and a fresh production check confirms zero legacy-
            // format rows remain, remove this branch entirely — a value
            // reaching here afterward would mean something regressed, and
            // should fail loudly instead of silently accepting plaintext.
            return stored;
        }

        int colonIdx = stored.indexOf(':');
        int version = Integer.parseInt(stored.substring(PREFIX.length(), colonIdx));
        SecretKeySpec key = FaceDataEncryptionKeyHolder.getKey(version);
        if (key == null) {
            throw new IllegalStateException(
                    "Stored face data was encrypted with key version " + version
                            + " but this instance has no matching key configured");
        }

        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(colonIdx + 1));
            byte[] nonce = Arrays.copyOfRange(combined, 0, GCM_NONCE_LENGTH);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_NONCE_LENGTH, combined.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, nonce));
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decrypt face data field", e);
        }
    }
}
