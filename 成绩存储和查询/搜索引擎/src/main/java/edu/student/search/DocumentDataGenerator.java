package edu.student.search;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Random;

/** Generates assignment-format document keyword-frequency records. */
public final class DocumentDataGenerator {
    private static final int KEYWORD_COUNT = 200;

    private DocumentDataGenerator() { }

    public static void main(String[] args) throws IOException {
        Path output = args.length >= 1 ? Paths.get(args[0]) : Paths.get("data/documents.txt");
        int count = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        generate(output, count, 20260622L);
        System.out.println("已生成 " + count + " 条文档记录: " + output);
    }

    /**
     * One line is: documentId keywordId:frequency keywordId:frequency ...
     * Each document gets 4--10 distinct keywords, which makes query results meaningful.
     */
    public static void generate(Path output, int count, long seed) throws IOException {
        if (count < 10_000) {
            throw new IllegalArgumentException("数据集记录数必须不少于 10000 条");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Random random = new Random(seed);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (int i = 1; i <= count; i++) {
                String documentId = String.format(Locale.ROOT, "D%05d", i);
                int keywordPerDocument = 4 + random.nextInt(7);
                Map<String, Integer> keywords = new LinkedHashMap<>();

                // Make the README's K0001 test query deterministic.
                if (i == 1) {
                    keywords.put("K0001", 6);
                }
                while (keywords.size() < keywordPerDocument) {
                    String keywordId = String.format(Locale.ROOT, "K%04d", 1 + random.nextInt(KEYWORD_COUNT));
                    keywords.put(keywordId, 1 + random.nextInt(20));
                }

                writer.write(documentId);
                for (Map.Entry<String, Integer> entry : keywords.entrySet()) {
                    writer.write(' ');
                    writer.write(entry.getKey());
                    writer.write(':');
                    writer.write(Integer.toString(entry.getValue()));
                }
                writer.newLine();
            }
        }
    }
}
