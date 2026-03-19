package com.huatai.rag.domain.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public final class IndexNamingPolicy {

    private IndexNamingPolicy() {
    }

    public static String indexNameFor(String filename) {
        try {
            byte[] digest = MessageDigest.getInstance("MD5").digest(filename.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.substring(0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("MD5 algorithm unavailable", exception);
        }
    }
}
