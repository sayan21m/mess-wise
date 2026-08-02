package com.srtech.messwise.utils;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Shared AES-256-GCM helpers for sensitive mess-scoped fields (UPI, email, …)
 * before Firebase storage.
 * <p>
 * Stored format: {@code mwenc1:} + Base64(IV || ciphertext || tag).
 * Key is derived per mess so authenticated clients in that mess can decrypt.
 */
public final class MessFieldCrypto {

    private static final String TAG = "MessFieldCrypto";
    public static final String PREFIX = "mwenc1:";
    private static final String TRANSFORM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;
    /** App-level pepper mixed into the per-mess key (not a substitute for RTDB rules). */
    private static final String APP_PEPPER = "MessWise.Upi.v1.SRTech.2026";

    private MessFieldCrypto() {}

    public static boolean isEncryptedPayload(@Nullable String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    /**
     * Encrypt arbitrary UTF-8 plaintext for a mess. Returns null if plaintext is null/empty.
     */
    @Nullable
    public static String encrypt(@NonNull String messId, @Nullable String plaintext) {
        if (plaintext == null) return null;
        String plain = plaintext.trim();
        if (plain.isEmpty()) return null;
        if (messId.trim().isEmpty()) {
            throw new IllegalArgumentException("messId required for field encryption");
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(messId), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] cipherBytes = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buffer = ByteBuffer.allocate(iv.length + cipherBytes.length);
            buffer.put(iv);
            buffer.put(cipherBytes);
            return PREFIX + Base64.encodeToString(buffer.array(), Base64.NO_WRAP);
        } catch (Exception e) {
            Log.e(TAG, "Field encrypt failed", e);
            throw new IllegalStateException("Could not encrypt field", e);
        }
    }

    /**
     * Decrypt a Firebase-stored value. If not encrypted, returns trimmed plaintext
     * (legacy) so existing data keeps working until re-saved.
     */
    @Nullable
    public static String decrypt(@NonNull String messId, @Nullable String stored) {
        if (stored == null) return null;
        String trimmed = stored.trim();
        if (trimmed.isEmpty()) return null;

        if (!isEncryptedPayload(trimmed)) {
            return trimmed;
        }

        if (messId.trim().isEmpty()) return null;

        try {
            byte[] raw = Base64.decode(trimmed.substring(PREFIX.length()), Base64.NO_WRAP);
            if (raw.length <= GCM_IV_LENGTH) return null;

            ByteBuffer buffer = ByteBuffer.wrap(raw);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] cipherBytes = new byte[buffer.remaining()];
            buffer.get(cipherBytes);

            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(messId), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(cipherBytes);
            String result = new String(plain, StandardCharsets.UTF_8).trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            Log.e(TAG, "Field decrypt failed", e);
            return null;
        }
    }

    @NonNull
    private static SecretKey deriveKey(@NonNull String messId) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] keyBytes = digest.digest(
                (APP_PEPPER + "|" + messId.trim()).getBytes(StandardCharsets.UTF_8));
        return new SecretKeySpec(keyBytes, "AES");
    }
}
