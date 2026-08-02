package com.srtech.messwise.utils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * AES-256-GCM encryption for member UPI IDs before Firebase storage.
 * Delegates to {@link MessFieldCrypto}; applies UPI normalization on encrypt/decrypt.
 * Stored format: {@code mwenc1:} + Base64(IV || ciphertext || tag).
 */
public final class UpiCrypto {

    private UpiCrypto() {}

    public static boolean isEncryptedPayload(@Nullable String stored) {
        return MessFieldCrypto.isEncryptedPayload(stored);
    }

    /**
     * Encrypt a normalized UPI VPA for a mess. Returns null if plaintext is null/empty.
     */
    @Nullable
    public static String encrypt(@NonNull String messId, @Nullable String plaintextUpi) {
        String plain = SettlementUtils.normalizeUpi(plaintextUpi);
        if (plain == null) return null;
        return MessFieldCrypto.encrypt(messId, plain);
    }

    /**
     * Decrypt a Firebase-stored value. Supports legacy plaintext VPAs
     * so existing data keeps working until re-saved.
     */
    @Nullable
    public static String decrypt(@NonNull String messId, @Nullable String stored) {
        if (stored == null) return null;
        String trimmed = stored.trim();
        if (trimmed.isEmpty()) return null;

        if (!isEncryptedPayload(trimmed)) {
            return SettlementUtils.normalizeUpi(trimmed);
        }

        String plain = MessFieldCrypto.decrypt(messId, trimmed);
        return SettlementUtils.normalizeUpi(plain);
    }
}
