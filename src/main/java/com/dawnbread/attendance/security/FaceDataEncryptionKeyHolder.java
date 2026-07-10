package com.dawnbread.attendance.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Same static-bridge pattern as AuditLogRepositoryHolder: FaceDataEncryptionConverter
 * is instantiated by JPA directly (via @Convert), not Spring, so it can't be
 * @Autowired — this bean grabs the Spring-managed config value at startup and
 * exposes it through a static accessor.
 *
 * Keyed by version so a future key rotation can add a new version here while
 * still decrypting rows written under the old one (see the design doc's key
 * management section). Only version 1 exists today.
 *
 * If face.data.encryption.key is unset (local dev/test, or before the Render
 * env var is configured), no key is loaded and the converter operates in
 * pass-through mode — reads/writes plaintext unchanged, identical to
 * behavior before this feature existed. This is deliberate: it's what makes
 * the dual-read rollout non-breaking for every environment that hasn't been
 * given a key yet.
 */
@Component
public class FaceDataEncryptionKeyHolder {

    private static final Map<Integer, SecretKeySpec> KEYS = new ConcurrentHashMap<>();

    public FaceDataEncryptionKeyHolder(@Value("${face.data.encryption.key:}") String base64Key) {
        KEYS.clear();
        if (base64Key != null && !base64Key.isBlank()) {
            byte[] raw = Base64.getDecoder().decode(base64Key);
            if (raw.length != 32) {
                throw new IllegalStateException(
                        "face.data.encryption.key must decode to exactly 32 bytes (AES-256) — got " + raw.length);
            }
            KEYS.put(1, new SecretKeySpec(raw, "AES"));
        }
    }

    static SecretKeySpec getKey(int version) {
        return KEYS.get(version);
    }

    static boolean hasAnyKeyConfigured() {
        return !KEYS.isEmpty();
    }
}
