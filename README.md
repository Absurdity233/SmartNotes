# 智趣笔记 · Smart Notes

一个 Android 笔记应用，支持 6 种笔记类型、离线可用、云端同步与多人协作。

Jetpack Compose + Room，配一个零依赖的 Node.js 服务端。

<!-- 截图建议放这里：把首页截图命名为 docs/screenshot-home.png 后取消注释 -->
<!-- ![首页](docs/screenshot-home.png) -->

## 它长什么样

- **6 种笔记**：普通笔记、日记、清单、待办、日程、图文
- **离线优先**：所有操作先落本地 Room，断网照常用，联网自动补传
- **多人协作**：把笔记分享给别人（可编辑 / 只读），对方改动会自动同步过来

日记有心情和天气，清单有进度条，待办有优先级，日程能设闹钟提醒。支持深色模式、回收站、归档、全文搜索。

## 快速开始

只在本地跑，三步：

```bash
# 1. 起服务端（可选，不起也能用全部本地功能）
cd server && node server.js          # 需要 Node.js ≥ 22.5

# 2. 构建 App
./gradlew :app:assembleDebug

# 3. 装到设备
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

首次启动服务端会写入两个演示账号：

| 账号 | 密码 |
| --- | --- |
| `absurdity` | `absurdity123` |
| `alice` | `alice123` |

App 默认连模拟器宿主机 `http://10.0.2.2:8787`，模拟器上无需任何配置。用真机的话，在「我的 → 云服务地址」改成电脑的局域网 IP。

**从 GitHub 克隆后**需要自己补两样东西（它们因机器而异，没进版本库）：

```bash
# Android SDK 路径
echo "sdk.dir=/path/to/Android/Sdk" > local.properties
```

如果本机 JDK 和 Gradle 不兼容（比如 JDK 25 配 Gradle 8.14），在 `~/.gradle/gradle.properties` 里指定 JDK 21，别改项目里的文件：

```properties
org.gradle.java.home=/path/to/jdk21
```

## 想给别人演示？

局域网 IP 只在自己 Wi-Fi 下有效。如果对方在别的网络，用 Cloudflare Tunnel 把本地服务映射成公网地址，不需要公网 IP 也不用配路由器：

```bash
# 下载一次 cloudflared，放到 tools/bin/
curl -L -o tools/bin/cloudflared.exe \
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe

# 起服务 + 隧道
run-public.bat
```

终端会打印一个 `https://xxx.trycloudflare.com` 地址。想把它直接编进 APK：

```bash
./gradlew :app:assembleDebug -Psmartnotes.server=https://xxx.trycloudflare.com
```

装到任何手机上开箱即用，不用再手填地址。不传这个参数时回落到 `http://10.0.2.2:8787`。

免费隧道每次重启都会换地址，这是 Cloudflare 的限制，不是 bug。

## 代码结构

```
app/src/main/java/com/nexo/smartnotes/     41 个 Kotlin 文件，约 7200 行
├── domain/          模型层，纯 Kotlin 不依赖 Android（Note、NoteType、NoteFilter）
├── data/
│   ├── local/       Room：实体、DAO、数据库与迁移
│   ├── remote/      OkHttp + kotlinx.serialization 客户端
│   ├── Prefs.kt     DataStore（账号、主题、服务器地址）
│   └── NotesRepository.kt   唯一数据入口，UI 只跟它打交道
├── ui/              Compose 界面，按页面分目录
└── util/            密码哈希、日期格式化、提醒调度

server/server.js    零依赖 Node 服务端，约 700 行
```

## 同步是怎么做的

本地每条笔记带 `dirty`（待同步）和 `serverSeq`（服务器版本号）两个标记。

写入后 1.5 秒防抖触发一次同步，把 `dirty` 的笔记和评论推给服务端，同时带上 `since = lastSyncSeq` 只拉回增量。服务端用 `updatedAt` 判冲突，过期写入会被拒并返回 `rejected`，客户端保留本地较新的版本等下一轮再推。

删除走「墓碑 + removed 列表」，多端才能保持一致。

App 在前台时每 20 秒自动拉一次，回到前台立刻拉，退到后台就停。所以协作者改完东西，你这边不用点任何按钮就能看到。

## 权限

笔记有 `ownerId`。`shares` 表记录谁被分享、什么权限（`edit` / `view`）。服务端对每次写入都校验，只有创建者和 `edit` 协作者能改。

## 几个实现上的取舍

**数据库迁移不用 `fallbackToDestructiveMigration`。** 那玩意会在版本号变化时静默删表，用户笔记全没。改成显式迁移，现在到 v3：

- v1 → v2：加复合索引 `(accountId, trashed, archived)`
- v2 → v3：加 `searchBlob` 列（小写全字段检索串），回填历史数据，把搜索下推到 SQL

**同步加了 Mutex。** 前台轮询、写入防抖、手动点同步可能同时触发，不加锁会把同一批 `dirty` 数据重复提交。

**用户搜索要求至少 2 个字符。** 之前输入单个字母能把全站账号翻出来，改成前缀匹配且单次最多返回 8 条。

**游客笔记只认一个人。** 退出登录后以游客身份写的笔记，只会并入「刚退出的那个账号」，不会被下一个登录的人顺走。

**删除标记会定期清理。** `removed_notes` 不清理会无限增长拖慢同步，服务端每小时清一次，保留 30 天内的。

## 测试

```bash
# 单元测试：搜索匹配、权限判断、进度计算、类型解析（28 项）
./gradlew :app:testDebugUnitTest

# 端到端：认证、同步、6 种类型字段、多用户隔离、分享权限、
#         只读拒绝、冲突检测、删除传播、评论、图片上传（43 项断言）
cd server && node server.js 8787 &
node tools/e2e-server-test.mjs http://127.0.0.1:8787
```

`tools/` 下还有几个排查用的脚本：

```bash
node tools/db-inspect.cjs <db>        # 看本地库的笔记类型、分区、密码哈希
node tools/migration-check.cjs <db>   # 迁移后的版本号、索引、数据条数
node tools/searchblob-check.cjs <db>  # searchBlob 回填质量
node tools/cleanup-test-users.cjs     # 清掉测试账号，只留 absurdity / alice
```

## 已知问题

- 服务端是明文 HTTP，token 不过期，没有速率限制。这是课程演示的取舍，真上线得换 HTTPS 和正经鉴权。
- 图片存在应用私有目录，上传后以 `/uploads/<hash>.jpg` 共享，没做压缩。
- 只支持单机服务端，没有水平扩展。
- 免费隧道的地址会变。

## License

MIT
