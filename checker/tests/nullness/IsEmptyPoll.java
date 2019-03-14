// Test case for Issue 399:
// https://github.com/typetools/checker-framework/issues/399

// @skip-test until the issue is fixed

import java.util.LinkedList;
import java.util.Queue;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class IsEmptyPoll {

    void pollNonNull(Queue<String> q) {
        while (!q.isEmpty()) {
            @NonNull String firstNode = q.poll();
        }
    }

    void pollNullable(Queue<@Nullable String> q) {
        while (!q.isEmpty()) {
            // :: error: (assignment.type.incompatible)
            @NonNull String firstNode = q.poll();
        }
    }

    void pollNoCheck(Queue<@Nullable String> q) {
        // :: error: (assignment.type.incompatible)
        @NonNull String firstNode = q.poll();
    }

    void peekNonNull(LinkedList<String> q) {
        while (!q.isEmpty()) {
            @NonNull String firstNode = q.peek();
        }
    }

    void peekNullable(LinkedList<@Nullable String> q) {
        while (!q.isEmpty()) {
            // :: error: (assignment.type.incompatible)
            @NonNull String firstNode = q.peek();
        }
    }

    void peekNoCheck(LinkedList<@Nullable String> q) {
        // :: error: (assignment.type.incompatible)
        @NonNull String firstNode = q.peek();
    }
}
