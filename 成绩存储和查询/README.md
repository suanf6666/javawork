# 成绩存储和查询（题目 2）

本项目使用 Java 和 HBase 完成成绩数据的生成、存储和查询。生成的数据每行格式为：

```text
学号 课程号 成绩
2026000001 C001 86
```

生成器会生成不少于 10,000 条记录，并让部分 `学号 + 课程号` 重复，以模拟补考、重修。

## HBase 表设计

表名为 `cs`，列族为 `info`。行键采用：

```text
学号#课程号#第几次记录
```

例如 `2026000001#C001#00002`。末尾的记录序号保证重修、补考成绩不会覆盖之前的成绩。

`info` 列族中保存：`studentId`、`courseId`、`score`、`attempt`、`importedAt`。

## 1. 生成模拟数据

在项目目录执行：

```powershell
javac -encoding UTF-8 -d out src/main/java/edu/student/score/ScoreDataGenerator.java
java -cp out edu.student.score.ScoreDataGenerator data/scores.txt 10000
```

会得到 `data/scores.txt`，共 10,000 条成绩记录。

## 2. IDE 中配置与测试

1. 使用 IntelliJ IDEA 打开项目根目录，选择 **Open as Maven Project**。
2. 将实验环境的 `hbase-site.xml` 复制到 `src/main/resources/`，或在运行配置中设置 HBase/ZooKeeper 参数。
3. 先运行 `generate data/scores.txt 10000`。
4. 启动 HBase 后运行 `import data/scores.txt`。程序会先检测 `cs` 是否存在，不存在时才创建。
5. 运行 `student 2026000001` 按学号查成绩，或运行 `course C001` 按课程号查成绩。

## 3. Maven 打包与集群运行

如果本机已安装 Maven：

```powershell
mvn clean package
```

生成的可运行 jar 在 `target/score-storage-query-1.0.0.jar`。把 jar 和数据上传到 HDFS 后，在 HBase 客户端节点运行（命令按实际 HBase 安装目录调整）：

```bash
hadoop fs -mkdir -p /coursework/score
hadoop fs -put data/scores.txt /coursework/score/
hbase org.apache.hadoop.util.RunJar score-storage-query-1.0.0.jar import data/scores.txt
hbase org.apache.hadoop.util.RunJar score-storage-query-1.0.0.jar student 2026000001
```

> HBase 客户端通常需要能读到 `hbase-site.xml`，否则须补充 `-Dhbase.zookeeper.quorum=主机名` 等配置。

## 4. HBase Shell 验证

导入后进入 HBase Shell：

```ruby
list
describe 'cs'
count 'cs'
scan 'cs', { LIMIT => 10 }
scan 'cs', { ROWPREFIXFILTER => '2026000001#' }
```

预期可以看到表 `cs`、列族 `info`，`count` 为 10000（第一次导入时），以及每条记录的学号、课程号、成绩和第几次记录。再次运行导入会使用相同的行键覆盖相同行的数据，而不会意外创建新表。
