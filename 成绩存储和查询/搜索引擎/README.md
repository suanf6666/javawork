# 搜索引擎（题目 4）

本项目使用 Java 和 HBase 完成“文档关键词检索”。它与父目录的题目二完全独立，使用同一套已经验证可运行的 Java 8 与 HBase 2.4.17 版本。

## 表设计

表名为 `doc`，列族为 `kw`。

| HBase 部分 | 对应内容 | 示例 |
| --- | --- | --- |
| 行键 | 文档 ID | `D00001` |
| 列族 | 关键词数据 | `kw` |
| 列限定符 | 关键词 ID | `K0001` |
| 单元格值 | 该词在该文档中的出现次数 | `6` |

因此输入行：

```text
D00001 K0001:6 K0023:2 K0104:9
```

会被写入为：`doc/D00001` 行中的 `kw:K0001=6`、`kw:K0023=2`、`kw:K0104=9`。查询关键词 `K0001` 后，程序按题目要求输出 `文档ID:出现次数`。

## 1. 生成不少于 10,000 条数据

在本目录执行：

```bash
mvn clean package
java -cp target/search-engine-1.0.0.jar edu.student.search.DocumentDataGenerator data/documents.txt 10000
```

也可以用主程序生成：

```bash
java -jar target/search-engine-1.0.0.jar generate data/documents.txt 10000
```

生成的数据中每个文档有 4 到 10 个互不重复的关键词；`K0001` 一定至少出现于 `D00001`，方便复现实验。

## 2. 在 HBase 环境导入与检索

先确认 HBase 正常运行：

```bash
start-hbase.sh
hbase shell
status 'simple'
exit
```

在本项目目录运行：

```bash
hbase org.apache.hadoop.util.RunJar target/search-engine-1.0.0.jar import data/documents.txt
hbase org.apache.hadoop.util.RunJar target/search-engine-1.0.0.jar search K0001
```

程序先检查表 `doc` 是否存在，仅在不存在时创建它。重复导入同一份数据会覆盖相同文档的同名关键词值，不会额外增加文档行。

若不是通过 `hbase ... RunJar` 启动，而是直接用 `java -jar`，需要将虚拟机中可用的 `hbase-site.xml` 放到 `src/main/resources/` 后重新打包，或自行传入 ZooKeeper 参数。

## 3. HBase Shell 验证

```ruby
list
describe 'doc'
count 'doc'
scan 'doc', { LIMIT => 5 }
get 'doc', 'D00001'
scan 'doc', { COLUMNS => ['kw:K0001'] }
```

预期：`doc` 有列族 `kw`，`count 'doc'` 为 `10000`，并且 `get 'doc', 'D00001'` 与数据文件首行对应。最后一条命令会显示含有 `kw:K0001` 的文档及出现次数。

## 命令总览

```text
generate <输出文件> <文档数>
import <数据文件>
search <关键词ID>
```
