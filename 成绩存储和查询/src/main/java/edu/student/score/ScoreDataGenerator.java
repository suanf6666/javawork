package edu.student.score;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Random;

/** Generates records in the assignment format: studentId courseId score. */
public final class ScoreDataGenerator {
    private static final String[] COURSES = {
            "C001", "C002", "C003", "C004", "C005", "C006", "C007", "C008",
            "C009", "C010", "C011", "C012", "C013", "C014", "C015", "C016"
    };

    private ScoreDataGenerator() { }

    public static void main(String[] args) throws IOException {
        Path output = args.length >= 1 ? Paths.get(args[0]) : Paths.get("data/scores.txt");
        int count = args.length >= 2 ? Integer.parseInt(args[1]) : 10_000;
        generate(output, count, 20260622L);
        System.out.println("已生成 " + count + " 条记录: " + output);
    }

    public static void generate(Path output, int count, long seed) throws IOException {
        if (count < 10_000) {
            throw new IllegalArgumentException("数据集记录数必须不少于 10000 条");
        }
        Path parent = output.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);

        Random random = new Random(seed);
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (int i = 0; i < count; i++) {
                // Restrict the student/course ranges intentionally so some pairs repeat as retakes.
                String studentId = String.format(Locale.ROOT, "2026%06d", 1 + random.nextInt(1_200));
                String courseId = COURSES[random.nextInt(COURSES.length)];
                int score = 30 + random.nextInt(71);
                writer.write(studentId + " " + courseId + " " + score);
                writer.newLine();
            }
        }
    }
}
