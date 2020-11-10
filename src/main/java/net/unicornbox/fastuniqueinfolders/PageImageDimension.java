package net.unicornbox.fastuniqueinfolders;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PageImageDimension {

    private final Map<Integer, Integer> pageImageSize = new ConcurrentHashMap<>();

    public PageImageDimension(Map<Integer, AtomicInteger> mimeTypes) {
        mimeTypes.forEach((k, v) -> {
            this.pageImageSize.put(k, v.intValue());
        });
    }

    public PageImageDimension() {

    }


    public PageImageDimension addForeign(PageImageDimension other) {
        other.pageImageSize.forEach((k, v) -> {
            pageImageSize.put(k, pageImageSize.getOrDefault(k, 0) + v);
        });
        return this;
    }

    public String toString() {

        StringBuilder sb = new StringBuilder();
        // map first to a list of segments, 100 per 100
        Map<Integer, Integer> steppedMap = new HashMap<>();
        // compute total at the same time
        AtomicLong total = new AtomicLong();
        pageImageSize.forEach((key1, value) -> {
            total.addAndGet(value);
            final int step = 100;
            int index = key1 / step;
            int key = index * step;
            final Integer steppedMapOrDefault = steppedMap.getOrDefault(key, 0);
            steppedMap.put(key, steppedMapOrDefault + value);
        });

        List<Map.Entry<Integer, Integer>> values =
                new LinkedList<>(steppedMap.entrySet());
        values.sort(Map.Entry.comparingByValue(Integer::compareTo));
        for (Map.Entry<Integer, Integer> entry : values) {
            sb.append(entry.getKey()).append("-").append(entry.getKey() + 99).append(":").append(entry.getValue()/(total.floatValue())  * 100).append("%").append("\n");
        }
        final ArrayList<Integer> sizes = new ArrayList<>(pageImageSize.keySet());
        sizes.sort(Integer::compareTo);
        sb.append("max value ").append(sizes.get(sizes.size() - 1)).append("\n");
        return sb.toString();
    }
}
