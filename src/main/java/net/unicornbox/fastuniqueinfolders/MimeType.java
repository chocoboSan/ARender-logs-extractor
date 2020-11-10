package net.unicornbox.fastuniqueinfolders;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MimeType {

    private final Map<String, Integer> mimeTypes = new ConcurrentHashMap<>();

    public MimeType(Map<String, AtomicInteger> mimeTypes) {
        mimeTypes.forEach((k, v) -> {
            this.mimeTypes.put(k, v.intValue());
        });
    }

    public MimeType() {

    }

    public MimeType addForeign(MimeType other) {
        other.mimeTypes.forEach((k, v) -> {
            mimeTypes.put(k, mimeTypes.getOrDefault(k, 0) + v);
        });
        return this;
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        AtomicLong total = new AtomicLong();
        mimeTypes.forEach((k, v) -> {
            total.addAndGet(v);
        });
        List<Map.Entry<String, Integer>> values =
                new LinkedList<>(mimeTypes.entrySet());
        values.sort(Map.Entry.comparingByValue((o1, o2) -> -1 * o1.compareTo(o2)));
        values.forEach((entry) -> {
            sb.append(entry.getKey()).append(":").append(entry.getValue() / total.floatValue() * 100).append("%").append("\n");
        });
        return sb.toString();
    }
}
