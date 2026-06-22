package edu.student.search;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.HBaseConfiguration;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Admin;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptorBuilder;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Result;
import org.apache.hadoop.hbase.client.ResultScanner;
import org.apache.hadoop.hbase.client.Scan;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.client.TableDescriptorBuilder;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/** Creates, imports and searches the HBase table required by assignment topic 4. */
public final class SearchApplication {
    private static final TableName TABLE = TableName.valueOf("doc");
    private static final byte[] FAMILY = Bytes.toBytes("kw");
    private static final int PUT_BATCH_SIZE = 1_000;

    private SearchApplication() { }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            usage();
            return;
        }
        switch (args[0]) {
            case "generate":
                requireArgs(args, 3);
                int count = Integer.parseInt(args[2]);
                DocumentDataGenerator.generate(Paths.get(args[1]), count, 20260622L);
                System.out.println("已生成 " + count + " 条文档记录: " + args[1]);
                break;
            case "import":
                requireArgs(args, 2);
                try (Connection connection = createConnection()) {
                    createTableIfMissing(connection);
                    importFile(connection, Paths.get(args[1]));
                }
                break;
            case "search":
                requireArgs(args, 2);
                try (Connection connection = createConnection()) {
                    searchKeyword(connection, args[1]);
                }
                break;
            default:
                usage();
        }
    }

    private static Connection createConnection() throws IOException {
        Configuration conf = HBaseConfiguration.create();
        // hbase-site.xml from the VM is read automatically when the program runs with `hbase RunJar`.
        return ConnectionFactory.createConnection(conf);
    }

    /** Creates doc only when it does not exist, preserving data from prior imports. */
    static void createTableIfMissing(Connection connection) throws IOException {
        try (Admin admin = connection.getAdmin()) {
            if (!admin.tableExists(TABLE)) {
                admin.createTable(TableDescriptorBuilder.newBuilder(TABLE)
                        .setColumnFamily(ColumnFamilyDescriptorBuilder.of(FAMILY))
                        .build());
                System.out.println("已创建 HBase 表 doc（列族：kw）");
            } else {
                System.out.println("HBase 表 doc 已存在，不重复创建。");
            }
        }
    }

    static void importFile(Connection connection, Path file) throws IOException {
        int imported = 0;
        List<Put> batch = new ArrayList<>(PUT_BATCH_SIZE);
        try (Table table = connection.getTable(TABLE);
             BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.trim().isEmpty()) {
                    continue;
                }
                batch.add(toPut(line, lineNumber));
                imported++;
                if (batch.size() == PUT_BATCH_SIZE) {
                    table.put(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                table.put(batch);
            }
        }
        System.out.println("导入完成，共写入 " + imported + " 条文档记录。");
    }

    private static Put toPut(String line, int lineNumber) throws IOException {
        String[] fields = line.trim().split("\\s+");
        if (fields.length < 2) {
            throw new IOException("格式错误（第 " + lineNumber + " 行，至少需要一个关键词）: " + line);
        }
        Put put = new Put(Bytes.toBytes(fields[0]));
        for (int i = 1; i < fields.length; i++) {
            String[] pair = fields[i].split(":", -1);
            if (pair.length != 2 || pair[0].isEmpty()) {
                throw new IOException("格式错误（第 " + lineNumber + " 行）: " + fields[i]);
            }
            try {
                int frequency = Integer.parseInt(pair[1]);
                if (frequency < 0) {
                    throw new NumberFormatException("negative");
                }
                put.addColumn(FAMILY, Bytes.toBytes(pair[0]), Bytes.toBytes(frequency));
            } catch (NumberFormatException e) {
                throw new IOException("出现次数必须是非负整数（第 " + lineNumber + " 行）: " + fields[i], e);
            }
        }
        return put;
    }

    /** Outputs exactly the required `documentId:frequency` format for one keyword. */
    static void searchKeyword(Connection connection, String keywordId) throws IOException {
        byte[] qualifier = Bytes.toBytes(keywordId);
        Scan scan = new Scan().addColumn(FAMILY, qualifier).setCaching(500);
        int matches = 0;
        try (Table table = connection.getTable(TABLE);
             ResultScanner results = table.getScanner(scan)) {
            for (Result result : results) {
                byte[] value = result.getValue(FAMILY, qualifier);
                if (value != null) {
                    System.out.println(Bytes.toString(result.getRow()) + ":" + Bytes.toInt(value));
                    matches++;
                }
            }
        }
        System.out.println("共找到 " + matches + " 篇包含关键词 " + keywordId + " 的文档。");
    }

    private static void requireArgs(String[] args, int expected) {
        if (args.length != expected) {
            throw new IllegalArgumentException("参数数量不正确。");
        }
    }

    private static void usage() {
        System.out.println("用法：generate <输出文件> <文档数> | import <数据文件> | search <关键词ID>");
    }
}
