# DB Chronicle Map 快照恢复

## 启动方式

org.evd.BootStrap.Main 在创建 Node 和 Service 前调用
DbSnapshotRecoveryMain.run()：

- 返回 `false`：本地快照文件不存在，Bootstrap 继续启动游戏。
- 返回 `true`：本地快照文件存在且恢复处理成功；Bootstrap 停止本次启动，重启后
  启动游戏服务。即使文件中没有记录，也会清理空文件并返回 `true`。
- 配置、快照、DB 或清理失败会直接抛出异常，Bootstrap 启动失败并保留未完成
  的快照文件。

也可以单独运行恢复入口：

~~~text
java <Chronicle JVM 参数> -cp <完整运行时 classpath> org.evd.game.DBService.DbSnapshotRecoveryMain Bootstrap-all.yml 0
~~~

仓库目前没有独立的生产启动脚本；Bootstrap 主入口已经执行该检查。
若部署脚本直接启动 BootStrap.Main，必须保留恢复入口，并在 Java 命令上
添加下方 JVM 参数。

恢复入口目前通过本 Node 的 DbYml 直连 MySQL，只接受 NODE_LOCAL 路由。
若配置为 REMOTE_SERVICE 且存在待恢复快照，入口抛出异常并保留快照，避免
把数据写到无法确认的数据库目标。

## Chronicle Map JVM 参数

Chronicle 2026.1 在 Java 17 及以上需要开放下列 JDK 包。Gradle 测试任务已
配置这些参数；生产启动命令也必须添加它们：

~~~text
--add-exports=java.base/jdk.internal.misc=ALL-UNNAMED
--add-exports=java.base/jdk.internal.ref=ALL-UNNAMED
--add-exports=java.base/jdk.internal.util=ALL-UNNAMED
--add-exports=java.base/sun.nio.ch=ALL-UNNAMED
--add-exports=jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED
--add-exports=jdk.unsupported/sun.misc=ALL-UNNAMED
--add-opens=java.base/java.io=ALL-UNNAMED
--add-opens=java.base/java.lang=ALL-UNNAMED
--add-opens=java.base/java.lang.reflect=ALL-UNNAMED
--add-opens=java.base/java.util=ALL-UNNAMED
--add-opens=jdk.compiler/com.sun.tools.javac=ALL-UNNAMED
~~~

## 文件与容量

- 快照目录由 `db.snapshotStore.directory` 配置，`config/db-dev.yml` 默认值为
  `data/db-snapshots`。相对路径按进程工作目录解析，也支持绝对路径；
  `-Devd.db.snapshot.dir=<本机持久目录>` 仍可覆盖配置。
- 每个 Node 使用一个 `node-<platform>-<server>-<node>.map` 文件；旁边的
  `.lock` 文件会保留，供进程之间互斥使用。
- `db.snapshotStore.entries` 和 `averageValueSizeKb` 配置新建 Map 的容量。
  已存在的 Map 保持创建时的容量；恢复时按文件头中的原始配置打开。只有旧 Map
  为空并删除后，当前配置的容量才会用于新文件。
- 正常关闭时，所有 Service 关闭后若 Map 为空，会删除 `.map` 文件。Bootstrap
  启动前也会检查已有文件：无待恢复记录时删除空文件；恢复全部成功且 Map 清空时
  删除文件。恢复失败或仍有记录时保留 `.map` 文件。
- 快照只保存发生修改且尚未收到成功 DBRsp 的实体；不会复制所有在线玩家。
- BufferPool 序列化输出会在 Chronicle Map 同步编码完成后归还。实体超过
  OutputStream 可用空间或 Map 容量时，发布失败并保留 dirty 状态。

## 实体序列化

DB APT 不再给生成的数据库实体添加 `@SerializeClass`。所有 DB 实体快照都复用
DB Bean 上的 `public static final Schema<DBBean> PROTOSTUFF_SCHEMA`，由
`RuntimeSchema` 为每个 DB Bean 初始化一次。PB 表数据库写入也复用该 Schema；
JSON/MYSQL 表原有的数据库行格式保持不变。

dbDef 的 `@DBDirtyTag` 是字段号的唯一来源；生成的 DB Bean 会把它转换为
Protostuff `@Tag`，包括 Map/List/Set 字段。Tag 标在集合字段上，集合元素不需要另加 Tag。新增
字段使用新的 Tag；已使用的 Tag 不得改号或复用。按 Tag 解码时，新增字段保留
构造器默认值，未知字段会被 Protostuff 跳过。结构指纹变化会记录警告并继续按
Tag 恢复。

Protostuff 输出直接写入现有 BufferPool 输出缓冲区。快照格式版本为 3；旧版本的
快照会保留并报不兼容，不能按新编码直接解码。实体运行时
脏数据版本在恢复后重置。

## 恢复与可靠性边界

恢复时先读当前 MySQL 行。若当前实体 payload 与快照逐字节一致，直接删除该
快照；否则用当前 APT 生成表构造单行 DBReq 并提交。MySQL 行版本不低于快照但
payload 不同时停止启动，避免旧快照覆盖较新的行。失败或提交结果未知时保留
快照。

TTable 的 ADD/GET 最终是 MySQL REPLACE，REMOVE 是按主键 DELETE，适合逐行
重放。Mdb.asyncSyncNow(Set) 保留跨表事务原子性，未拆成独立行快照；若这类
事务也需要 JVM 崩溃恢复，需要额外的事务操作日志。

按当前 DBRsp 语义，DbCache 接收成功也视为保存成功，并清理对应 Chronicle 项。
DbCache 本身是进程内存缓存；若 JVM 在其刷入 MySQL 前崩溃，该条缓存不会由
Chronicle 快照恢复。项目默认 enableMemoryCache 为 false。

Chronicle Map 是内存映射数据结构，不是事务日志。put() 返回不等价于 fsync；
OS 调度磁盘写回，正常关闭会写回 Map。JVM 异常退出后，恢复入口使用
`recoverPersistedTo(file, false)` 从文件头读取创建时的 Builder 配置并修复可恢复
状态。配置中的新容量只用于旧文件被清空删除后的新 Map。操作系统崩溃或断电仍可能
丢失尚未写回磁盘的数据。Map checksum 能检测部分损坏，但不能提供 MySQL 与本地
文件之间的原子事务保证。
