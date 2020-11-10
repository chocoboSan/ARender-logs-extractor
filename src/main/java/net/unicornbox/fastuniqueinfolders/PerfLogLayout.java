package net.unicornbox.fastuniqueinfolders;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class PerfLogLayout {
    private static final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SS");
    private Date start = null;
    private Date end = null;
    private long layoutCount = 0;
    private Map<Integer, AtomicInteger> imageDimension = new ConcurrentHashMap<>();
    private Map<Integer, AtomicInteger> thumbDimension = new ConcurrentHashMap<>();
    private Map<String, AtomicInteger> mimeTypes = new ConcurrentHashMap<>();

    public PerfLogLayout() {

    }

    public void setStart(String date) {
        // parse date
        start = parseDate(date);
    }

    private Date parseDate(String date) {
        try {
            return simpleDateFormat.parse(date);
        } catch (ParseException e) {
            System.err.println("cannot parse date");
            return new Date();
        }
    }

    public Date getStart() {
        return start;
    }

    public Date getEnd() {
        return end;
    }

    public void setEnd(String date) {
        // parse date
        end = parseDate(date);
    }

    public void incrementLayout() {
        layoutCount++;
    }

    public void addImageSize(Integer imageAsInt) {
        if (imageAsInt > 250) {
            imageDimension.putIfAbsent(imageAsInt, new AtomicInteger(0));
            imageDimension.get(imageAsInt).incrementAndGet();
        } else {
            thumbDimension.putIfAbsent(imageAsInt, new AtomicInteger(0));
            thumbDimension.get(imageAsInt).incrementAndGet();
        }
    }

    public void addMimeType(String mimeType) {
        mimeTypes.putIfAbsent(mimeType, new AtomicInteger(0));
        mimeTypes.get(mimeType).incrementAndGet();
    }

    public MimeType getMimeTypesDistribution() {
        return new MimeType(mimeTypes);
    }

    public double getAverageImageWidth() {
        return computeAverage(imageDimension);
    }

    private double computeAverage(Map<Integer, AtomicInteger> imageDimension) {
        long totalElems = 0;
        long sumWidth = 0;
        for (Map.Entry<Integer, AtomicInteger> entry : imageDimension.entrySet()) {
            Integer k = entry.getKey();
            Integer v = entry.getValue().get();
            totalElems += v;
            for (int i = 0; i < v; i++) {
                sumWidth += k;
            }
        }
        return sumWidth / ((double) totalElems);
    }

    public PageImageDimension getImageDimension() {
        return new PageImageDimension(imageDimension);
    }

    public double getAverageThumbWidth() {
        return computeAverage(thumbDimension);
    }


    public double getAverage() {
        return ((double) layoutCount) / (end.toInstant().getEpochSecond() - start.toInstant().getEpochSecond());
    }
}
