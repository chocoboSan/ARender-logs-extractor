package net.unicornbox.fastuniqueinfolders;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootApplication
public class FastUniqueInFoldersApplication implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(FastUniqueInFoldersApplication.class, args);
    }

    @Override
    public void run(String... args) throws Exception {
        // input, a list of folder
        final List<String> folders = Arrays.stream(args).collect(Collectors.toList());
        Map<String, Set<String>> idsPerFolder = new HashMap<>();
        // generate list of unique IDs, per folder
        for (String folder : folders) {
            Set<String> idsInFolder = new HashSet<>();
            for (File subFile : new File(folder).listFiles()) {
                if (subFile.isDirectory()) {
                    // handle directory
                    handleDirectoryForPerf(subFile, idsInFolder);
                }
            }
            idsPerFolder.put(folder, idsInFolder);
        }

        Set<String> uniqueIdsInCommon = new HashSet<>();
        idsPerFolder.forEach((folderName, set) -> {
            for (String id : set) {
                if (uniqueIdsInCommon.contains(id)) {
                    continue;
                }
                boolean found = true;
                for (Map.Entry<String, Set<String>> entry : idsPerFolder.entrySet()) {
                    if (!entry.getValue().contains(id)) {
                        found = false;
                        break;
                    }
                }
                if (found) {
                    uniqueIdsInCommon.add(id);
                }
            }
        });
        uniqueIdsInCommon.forEach(System.out::println);
        // now find in server logs where those IDs appear

        // generate list of unique IDs, per folder
        for (String folder : folders) {
            Set<String> idsInFolder = new HashSet<>();
            for (File subFile : new File(folder).listFiles()) {
                if (subFile.isDirectory()) {
                    // handle directory
                    handleDirectoryForServer(subFile, idsInFolder, uniqueIdsInCommon);
                }
            }
            idsInFolder.forEach(System.out::println);
        }

        // now count the average, per server, number of documents opened when crashing (and list any potential mime type)

        for (String folder : folders) {
            final File folderFile = new File(folder);
            List<Double> averages = new ArrayList<>();
            List<Double> averagesSizes = new ArrayList<>();
            List<Double> averagesSizesT = new ArrayList<>();
            MimeType mimeTypes = new MimeType();
            PageImageDimension pageImageDimension = new PageImageDimension();
            for (File subFile : folderFile.listFiles()) {
                if (subFile.isDirectory()) {
                    final List<PerfLogLayout> perfLogLayouts = countLayouts(subFile);
                    final OptionalDouble average = perfLogLayouts.stream().mapToDouble(PerfLogLayout::getAverage).average();
                    final OptionalDouble averageSize = perfLogLayouts.stream().mapToDouble(PerfLogLayout::getAverageImageWidth).average();
                    final OptionalDouble averageSizeT = perfLogLayouts.stream().mapToDouble(PerfLogLayout::getAverageThumbWidth).average();
                    perfLogLayouts.forEach(perfLogLayout -> mimeTypes.addForeign(perfLogLayout.getMimeTypesDistribution()));
                    if (average.isPresent()) {
                        averages.add(average.getAsDouble());
                        System.out.printf("For folder %s server %s, average layouts per second : %s%n", folderFile.getName(), subFile.getName(), average.getAsDouble());
                    } else {
                        System.out.printf("For folder %s server %s, no performance logs%n", folderFile.getName(), subFile.getName());
                    }
                    if (averageSize.isPresent()) {
                        averagesSizes.add(averageSize.getAsDouble());
                        System.out.printf("For folder %s server %s, average full size picture : %s%n", folderFile.getName(), subFile.getName(), averageSize.getAsDouble());
                    }
                    if (averageSizeT.isPresent()) {
                        averagesSizesT.add(averageSizeT.getAsDouble());
                        System.out.printf("For folder %s server %s, average thumb picture : %s%n", folderFile.getName(), subFile.getName(), averageSizeT.getAsDouble());
                    }
                    PageImageDimension loc = new PageImageDimension();
                    for (PerfLogLayout perfLogLayout : perfLogLayouts) {
                        loc.addForeign(perfLogLayout.getImageDimension());
                    }
                    pageImageDimension.addForeign(loc);
                    System.out.printf("For folder %s server %s, distribution of pageImageSize : %n%s%n", folderFile.getName(), subFile.getName(), loc.toString());
                }
            }
            final OptionalDouble average = averages.stream().mapToDouble(Double::doubleValue).average();
            if (average.isPresent()) {
                System.out.printf("For folder %s, average layouts per second : %s%n", folderFile.getName(), average.getAsDouble());
            }
            final OptionalDouble averageSize = averagesSizes.stream().mapToDouble(Double::doubleValue).average();
            if (averageSize.isPresent()) {
                System.out.printf("For folder %s, average full size picture : %s%n", folderFile.getName(), averageSize.getAsDouble());
            }
            final OptionalDouble averageSizeT = averagesSizesT.stream().mapToDouble(Double::doubleValue).average();
            if (averageSizeT.isPresent()) {
                System.out.printf("For folder %s, average thumb picture : %s%n", folderFile.getName(), averageSizeT.getAsDouble());
            }
            System.out.printf("For folder %s, MimeType distribution : %n%s%n", folderFile.getName(), mimeTypes.toString());
            System.out.printf("For folder %s, PageImage distribution : %n%s%n", folderFile.getName(), pageImageDimension.toString());
            System.out.println("");
        }
    }

    private List<PerfLogLayout> countLayouts(File subFile) throws InterruptedException {
        final ArrayList<PerfLogLayout> perfLogLayouts = new ArrayList<>();
        File[] listFiles = subFile.listFiles((dir, name) -> name.contains("perf"));
        if (listFiles == null) {
            return perfLogLayouts;
        }
        for (File perfLog : listFiles) {
            perfLogLayouts.add(countSinglePerfLogLayouts(perfLog));
        }
        return perfLogLayouts;
    }

    private PerfLogLayout countSinglePerfLogLayouts(File perfLog) throws InterruptedException {
        PerfLogLayout perfLogLayout = new PerfLogLayout();
        ScheduledExecutorService scheduledExecutorService = new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors());
        try (FileReader in = new FileReader(perfLog);
             BufferedReader br = new BufferedReader(in)) {
            String line = null;
            String previousLine = null;
            while ((line = br.readLine()) != null) {
                if (line.matches(".*b64_.*")) {
                    // this line contains an id, grep the ID
                    String finalLine = line;
                    final Matcher matcher = Pattern.compile(".*(b64_[a-z-A-Z0-9]+).*").matcher(line);
                    if (matcher.find()) {
                        final String group = matcher.group(1);
                        if (group.trim().substring(group.length() - 5).matches("[0-9]+")) {
                            continue;
                        }
                        if (line.contains("BaseDocumentService.getDocumentLayout")) {
                            perfLogLayout.incrementLayout();
                        }
                        final String[] split = line.substring(6).split(" ");
                        if (perfLogLayout.getStart() == null) {
                            perfLogLayout.setStart(split[0] + " " + split[1]);
                        }
                    }
                    scheduledExecutorService.execute(() -> {
                        final Matcher imageMatcher = Pattern.compile(".*(IM_[0-9]+_[0-9]+).*").matcher(finalLine);
                        if (imageMatcher.find()) {
                            final String image = imageMatcher.group(1);
                            perfLogLayout.addImageSize(Integer.parseInt(image.split("_")[1]));
                        }
                    });
                    scheduledExecutorService.execute(() -> {
                        final Matcher mimeTypeMatcher = Pattern.compile(".*MimeType=([a-zA-Z0-9]+/[a-zA-Z0-9-.]+).*").matcher(finalLine);
                        if (mimeTypeMatcher.find()) {
                            final String type = mimeTypeMatcher.group(1);
                            perfLogLayout.addMimeType(type);
                        }
                    });
                }
                previousLine = line;
            }
            final String[] split = previousLine.substring(6).split(" ");
            perfLogLayout.setEnd(split[0] + " " + split[1]);
        } catch (IOException e) {
            // not found
        }
        scheduledExecutorService.shutdown();
        scheduledExecutorService.awaitTermination(10, TimeUnit.MINUTES);
        return perfLogLayout;
    }

    private void handleDirectoryForPerf(File subFile, Set<String> ids) {
        for (File perfLog : subFile.listFiles((dir, name) -> name.contains("perf"))) {
            extractIds(perfLog, ids, false, null, subFile.getName());
        }
    }

    private void handleDirectoryForServer(File subFile, Set<String> ids, Set<String> matchingIds) {
        for (File perfLog : subFile.listFiles((dir, name) -> name.contains("server"))) {
            extractIds(perfLog, ids, true, matchingIds, subFile.getName());
        }
    }

    private void extractIds(File perfLog, Set<String> ids, boolean extractEntireLine, Set<String> matchingIds, String subFolderName) {
        try (FileReader in = new FileReader(perfLog);
             BufferedReader br = new BufferedReader(in)) {
            String line = null;
            while ((line = br.readLine()) != null) {
                if (line.matches(".*b64_.*")) {
                    // this line contains an id, grep the ID
                    Pattern p = Pattern.compile(".*(b64_[a-z-A-Z0-9]+).*");
                    final Matcher matcher = p.matcher(line);
                    if (matcher.find()) {
                        final String group = matcher.group(1);
                        if (group.trim().substring(group.length() - 5).matches("[0-9]+")) {
                            continue;
                        }
                        if (matchingIds != null) {
                            if (!matchingIds.contains(group)) {
                                continue;
                            }
                        }
                        final String[] split = line.split(" ");
                        ids.add(extractEntireLine ? String.format("%s %s %s %s", split[0], split[1], group, subFolderName) : group);
                    }
                }
            }
        } catch (IOException e) {
            // not found
        }
    }
}
