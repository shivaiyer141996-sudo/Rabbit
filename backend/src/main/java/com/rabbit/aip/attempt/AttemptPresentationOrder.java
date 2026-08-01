package com.rabbit.aip.attempt;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * Produces a stable, non-predictable-looking order for one assessment attempt.
 * The order is derived from persisted identifiers, so a refresh or resume never
 * changes the question or option position presented to the student.
 */
public final class AttemptPresentationOrder {

    private AttemptPresentationOrder() {
    }

    public static <T> List<T> order(
            UUID attemptId,
            String scope,
            List<T> source,
            Function<T, UUID> identifier,
            boolean shuffle
    ) {
        List<T> ordered = new ArrayList<>(source);
        if (!shuffle || ordered.size() < 2) return List.copyOf(ordered);
        ordered.sort(Comparator
                .comparingLong((T item) -> rank(
                        attemptId, scope, identifier.apply(item)
                ))
                .thenComparing(identifier));
        return List.copyOf(ordered);
    }

    static long rank(UUID attemptId, String scope, UUID itemId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(scope.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(uuidBytes(attemptId));
            digest.update(uuidBytes(itemId));
            return ByteBuffer.wrap(digest.digest()).getLong();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the JVM.", exception);
        }
    }

    private static byte[] uuidBytes(UUID value) {
        return ByteBuffer.allocate(16)
                .putLong(value.getMostSignificantBits())
                .putLong(value.getLeastSignificantBits())
                .array();
    }
}
