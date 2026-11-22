package com.cocos.challenge.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.regex.Pattern;

public record RequestFingerprint(String hex) {

    private static final Pattern LOWERCASE_SHA256 = Pattern.compile("[0-9a-f]{64}");

    public RequestFingerprint {
        if (hex == null || !LOWERCASE_SHA256.matcher(hex).matches()) {
            throw new IllegalArgumentException("Request fingerprint must be 64 lowercase hexadecimal characters.");
        }
    }

    public static RequestFingerprint of(String canonicalRequest) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalRequest.getBytes(StandardCharsets.UTF_8));
            return new RequestFingerprint(HexFormat.of().formatHex(hash));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
