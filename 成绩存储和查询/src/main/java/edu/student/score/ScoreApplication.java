package edu.student.score;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Get;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.filter.PrefixFilter;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Command-line program for creating, importing into and querying the HBase cs table. */
public final class ScoreApplication {
    private static final TableName TABLE = TableName.valueOf("cs");
    private static final byte[] FAMILY = Bytes.toBytes("info");

    private ScoreApplication() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) { usage(); return; }
        switch (args[0]) {
            case "generate":
                requireArgs(args, 3);
                int count = Integer.parseInt(args[2]);
                ScoreDataGenerator.generate(Paths.get(args[1]), count, 20260622L);
                System.out.println("已生成 " + count + " 条记录: " + args[1]);
                break;
            case "import":
                requireArgs(args, 2);
                try (Connection connection = createConnection()) {
                    createTableIfMissing(connection);
                    importFile(connection, Paths.get(args[1]));
                }
                break;
            case "student":
                requireArgs(args, 2);
                try (Connection connection = createConnection()) { queryByStudent(connection, args[1]); }
                break;
            case "course":
                requireArgs(args, 2);
                try (Connection connection = createConnection()) { queryByCourse(connection, args[1]); }
                break;
            default: usage();
        }
    }

    private static Connection createConnection() throws IOException {
        Configuration conf = HBaseConfiguration.create();
        // When running outside the HBase cluster, pass -Dhbase.zookeeper.quorum=host1,host2.
        return ConnectionFactory.createConnection(conf);
    }

    /** The table is created only if it does not already exist, as required. */
    static void createTableIfMissing(Connection connection) throws IOException {
        try (Admin admin = connection.getAdmin()) {
            if (!admin.tableExists(TABLE)) {
                admin.createTable(TableDescriptorBuilder.newBuilder(TABLE)
                        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY)).build());
                System.out.println("已创建 HBase 表 cs（列族：info）");
            } else {
                System.out.println("HBase 表 cs 已存在，不重复创建。");
            }
        }
    }

    static void importFile(Connection connection, Path file) throws IOException {
        int imported = 0;
        Map<String, Integer> attempts = new HashMap<>();
        try (Table table = connection.getTable(TABLE);
             BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                String[] fields = line.trim().split("[\\s,]+");
                if (fields.length != 3) throw new IOException("格式错误（第 " + (imported + 1) + " 行）: " + line);
                String pair = fields[0] + "#" + fields[1];
                int attempt = attempts.merge(pair, 1, Integer::sum);
                String rowKey = pair + "#" + String.format("%05d", attempt);
                Put put = new Put(Bytes.toBytes(rowKey));
                put.addColumn(FAMILY, Bytes.toBytes("studentId"), Bytes.toBytes(fields[0]));
                put.addColumn(FAMILY, Bytes.toBytes("courseId"), Bytes.toBytes(fields[1]));
                put.addColumn(FAMILY, Bytes.toBytes("score"), Bytes.toBytes(fields[2]));
                put.addColumn(FAMILY, Bytes.toBytes("attempt"), Bytes.toBytes(attempt));
                put.addColumn(FAMILY, Bytes.toBytes("importedAt"), Bytes.toBytes(Instant.now().toString()));
                table.put(put);
                imported++;
            }
        }
        System.out.println("导入完成，共写入 " + imported + " 条成绩记录。");
    }

    static void queryByStudent(Connection connection, String studentId) throws IOException {
        Scan scan = new Scan().setFilter(new PrefixFilter(Bytes.toBytes(studentId + "#")));
        try (Table table = connection.getTable(TABLE); ResultScanner results = table.getScanner(scan)) {
            printResults("学号 " + studentId + " 的成绩", results);
        }
    }

    static void queryByCourse(Connection connection, String courseId) throws IOException {
        Scan scan = new Scan();
        try (Table table = connection.getTable(TABLE); ResultScanner results = table.getScanner(scan)) {
            int number = 0;
            System.out.println("课程 " + courseId + " 的成绩：");
            for (Result result : results) {
                String actualCourse = Bytes.toString(result.getValue(FAMILY, Bytes.toBytes("courseId")));
                if (courseId.equals(actualCourse)) { printResult(result); number++; }
            }
            System.out.println("共 " + number + " 条记录。");
        }
    }

    private static void printResults(String title, ResultScanner results) {
        int number = 0;
        System.out.println(title + "：");
        for (Result result : results) { printResult(result); number++; }
        System.out.println("共 " + number + " 条记录。");
    }

    private static void printResult(Result result) {
        System.out.printf("%s  %s  成绩=%s  第%d次记录%n",
                Bytes.toString(result.getValue(FAMILY, Bytes.toBytes("studentId"))),
                Bytes.toString(result.getValue(FAMILY, Bytes.toBytes("courseId"))),
                Bytes.toString(result.getValue(FAMILY, Bytes.toBytes("score"))),
                Bytes.toInt(result.getValue(FAMILY, Bytes.toBytes("attempt"))));
    }

    private static void requireArgs(String[] args, int expected) {
        if (args.length != expected) throw new IllegalArgumentException("参数数量不正确。");
    }

    private static void usage() {
        System.out.println("用法：generate <输出文件> <记录数> | import <数据文件> | student <学号> | course <课程号>");
    }
}
