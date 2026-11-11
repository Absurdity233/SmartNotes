# 智趣笔记 · Smart Notes

> 课程项目 —— 一个支持 6 种笔记类型、本地存储、云端同步与多用户协作的 Android 笔记应用。

Jetpack Compose + Room + Node.js 零依赖云服务，完全离线可用，联网后自动增量同步。

---

## 一、功能清单

### 基础功能

| 功能 | 说明 |
| --- | --- |
| **新建 / 编辑笔记** | 富文本式标题与正文、置顶、卡片配色（9 种）、标签、提醒时间 |
| **笔记展示** | 瀑布流卡片 / 紧凑列表两种布局，置顶分区，类型徽章，完成进度条，图片缩略图 |
| **笔记搜索** | 全文检索（标题 / 正文 / 标签 / 清单条目 / 地点），支持多关键词与类型筛选 |
| **本地存储** | Room 数据库持久化，离线可用；退出、重启后数据完整保留 |

### 扩展功能

| 功能 | 说明 |
| --- | --- |
| **6 种笔记类型** | 📝 笔记、🌤️ 日记、📋 清单、✅ 待办、📅 日程、🖼️ 图文 |
| **在线存储** | 自研 Node.js 云服务，账号登录后自动增量同步（版本号 + 冲突检测） |
| **多用户** | 注册 / 登录 / 游客模式，数据按账号隔离，同设备可切换多个账号 |
| **协作** | 笔记分享给其他用户（可编辑 / 仅查看），实时评论，协作方勾选同步 |
| **提醒通知** | AlarmManager 精确闹钟 + 通知渠道，日程与待办到点提醒，重启后自动重排 |
| **其他** | 深色模式、回收站、归档、创建副本、图片云端上传、头像、日期选择器 |

### 每种笔记类型的能力

| 类型 | 专属能力 |
| --- | --- |
| 📝 笔记 TEXT | 纯文本 + 标签 |
| 🌤️ 日记 DIARY | 心情（8 种）、天气（7 种）、地点 |
| 📋 清单 CHECKLIST | 勾选条目、完成进度、进度条 |
| ✅ 待办 TODO | 每条独立的优先级（普通 / 重要 / 紧急）与截止时间 |
| 📅 日程 SCHEDULE | 开始 / 结束时间、全天、地点、提醒闹钟 |
| 🖼️ 图文 IMAGE | 多图上传、图片配文 |

---

## 二、运行方式

### 1. 云服务（可选，不启动也能使用全部本地功能）

需要 **Node.js ≥ 22.5**（使用内置 `node:sqlite`，无任何第三方依赖）。

```bash
cd server
node server.js          # 默认端口 8787
node server.js 9000     # 自定义端口
```

Windows 下也可以直接双击 `run-server.bat`。

启动后访问 <http://127.0.0.1:8787> 可以看到服务状态页，`/api/health` 为健康检查接口。

首次启动会自动创建演示数据：

| 账号 | 密码 | 说明 |
| --- | --- | --- |
| `demo` | `demo123` | 有 5 条示例笔记，其中清单已分享给 alice |
| `alice` | `alice123` | 协作方账号 |

### 2. Android 应用

**Android Studio**：直接 `Open` 本目录，同步 Gradle 后运行 `app`。

**命令行**：

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

- `minSdk 26`，`targetSdk 36`，Kotlin 2.1 / Compose BOM 2025.09
- 构建需要 JDK 17+（本机已在 `gradle.properties` 固定为 JDK 21，Gradle 8.14）

### 3. 连接云服务

应用默认地址为 `http://10.0.2.2:8787`（模拟器访问宿主机 localhost 的固定地址）。

| 运行环境 | 需要填写的地址 |
| --- | --- |
| Android 模拟器 | `http://10.0.2.2:8787`（默认，无需修改） |
| 真机（同一 Wi-Fi） | `http://<电脑局域网 IP>:8787`，例如 `http://192.168.1.10:8787` |

修改位置：**我的 → 云服务地址**，填好后点「测试连接」确认可达。

### 4. 公网访问（老师/同学不在同一 Wi-Fi 时）

本地 `http://127.0.0.1:8787` 只能自己访问，局域网 IP 又要求同一 Wi-Fi。用 Cloudflare Tunnel 把本地服务映射成一个公网 HTTPS 地址，**无需公网 IP、无需路由器端口映射、免费**：

```bash
# 1) 下载一次 cloudflared（约 55MB）放到 tools/bin/
curl -L -o tools/bin/cloudflared.exe ^
  https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe

# 2) 一键启动（本地服务 + 公网隧道）
run-public.bat
```

启动后终端会打印形如 `https://auto-camping-blast-wto.trycloudflare.com` 的地址，这就是公网入口。

**把公网地址编译进 APK**，装到任何手机上装完即用，不用再手填：

```bash
./gradlew :app:assembleDebug -Psmartnotes.server=https://auto-camping-blast-wto.trycloudflare.com
```

> 未传 `-Psmartnotes.server` 时仍回落到 `http://10.0.2.2:8787`（模拟器）。地址通过 `BuildConfig.SMARTNOTES_SERVER` 注入，见 `app/build.gradle.kts`。

公网地址是临时的（每次重启隧道都会变），答辩当天重跑一次 `run-public.bat` 拿到新地址即可。

| 场景 | 地址 |
| --- | --- |
| 模拟器 | `http://10.0.2.2:8787` |
| 同一局域网真机 | `http://<电脑局域网 IP>:8787` |
| **任意网络（公网）** | `https://<随机名>.trycloudflare.com` |

---

## 三、技术架构

```
app/src/main/java/com/nexo/smartnotes/
├── MainActivity.kt            入口，主题切换
├── SmartNotesApp.kt           手写依赖容器
├── domain/                    领域模型（纯 Kotlin，无 Android 依赖）
│   ├── Models.kt              Note / NoteType / ChecklistItem / Attachment / ShareInfo
│   └── Session.kt             LocalUser / Session / NoteFilter
├── data/
│   ├── local/                 Room：实体、DAO、数据库
│   ├── remote/                OkHttp + kotlinx.serialization 云客户端
│   ├── Prefs.kt               DataStore 偏好（账号、主题、服务器地址）
│   └── NotesRepository.kt     唯一数据入口：本地读写 + 增量同步 + 图片上传
├── ui/
│   ├── AppRoot.kt             Navigation Compose 路由
│   ├── AppViewModel.kt        应用级状态
│   ├── home/                  首页、搜索、日程、归档、回收站
│   ├── editor/                编辑器（按类型渲染不同形态）
│   ├── detail/                详情页 + 分享 + 评论
│   ├── account/               我的、登录注册
│   ├── components/            卡片、头像、标签、日期选择器、进度条
│   └── theme/                 配色、字体、深浅色主题
└── util/                      密码哈希、日期格式化、提醒调度

server/server.js               零依赖 Node.js 云服务（HTTP + node:sqlite）
```

### 同步设计

1. 本地每条笔记带 `dirty`（待同步）与 `serverSeq`（服务器版本号）标记。
2. 任何写操作后 1.5 秒内合并触发一次 `syncNow()`（防抖，避免频繁请求）。
3. 上传：把 `dirty = 1` 的笔记与评论推给 `/api/sync`，附带 `since = lastSyncSeq`。
4. 服务端按 `updatedAt` 做冲突检测，拒绝过期写入；返回 `accepted` / `rejected`。
5. 下载：服务端返回 `seq > since` 的可见笔记（自己的 + 别人分享的）与评论。
6. 本地若有未同步的更新且更新时间更晚，则保留本地，等下一轮再推（最后写入者优先）。
7. 删除采用「删除标记 + 服务端 removed 列表」，保证多端一致。
8. **前台自动拉取**：应用在前台期间每 20 秒静默 `syncNow()`，回到前台（`ON_START`）立即拉一次，退到后台（`ON_STOP`）即停止轮询。这样协作者在别处的修改无需手动点「同步」即可看到。

### 权限模型

- 笔记的 `ownerId` 为创建者；`shares` 表记录协作者与权限（`edit` / `view`）。
- 服务端对每次写入都校验权限：只有创建者或 `edit` 协作者可以修改。
- 客户端 `Note.canEdit` 控制编辑按钮、勾选交互与评论输入。

---

## 四、界面预览

| 首页 | 详情 | 编辑器 | 协作分享 |
| --- | --- | --- | --- |
| 卡片瀑布流、置顶分区、类型筛选 | 心情天气、清单勾选、共享成员、评论 | 按类型渲染的专属表单 | 搜索用户、设置权限、实时评论 |

支持浅色 / 深色主题（跟随系统或手动切换），统一使用 9 色卡片调色板与 6 种类型强调色。

---

## 五、健壮性设计

- **离线优先**：所有操作先写本地，网络失败不影响使用，联网后自动补传。
- **输入校验**：用户名格式、密码长度、分享对象是否存在、不能分享给自己。
- **密码安全**：本地账号使用 PBKDF2-HMAC-SHA256（12 万次迭代 + 随机盐）；云端使用 scrypt + 常量时间比较。
- **容错解析**：类型专属数据（清单 / 图片 / 分享）全部 `runCatching` 兜底，单条坏数据不会导致崩溃或同步中断。
- **同步降级**：云端不可用时注册自动降级为本地账号；登录失败回退本地校验。
- **提醒恢复**：开机广播与应用启动时都会重新排定未来提醒。
- **确认式破坏操作**：删除、清空回收站、退出登录均有二次确认。

### 数据库迁移（v1 → v2 → v3）

**不使用** `fallbackToDestructiveMigration`。它会在版本号变化时静默删表，
用户所有本地笔记一次性丢失 —— 对一个以「本地存储」为评分点的应用不可接受。

改为显式迁移，并在真机上验证过升级路径：

| 迁移 | 内容 |
| --- | --- |
| v1 → v2 | 新增复合索引 `(accountId, trashed, archived)`，覆盖首页 / 归档 / 回收站的常用查询 |
| v2 → v3 | 新增 `searchBlob` 列（小写全字段检索串）并回填历史数据，把搜索下推到 SQL |

实测：设备上导入 7 条笔记的 v1 库 → 安装 v2/v3 包 → `user_version` 升到 3，
**7 条笔记、1 个用户、1 条评论一条不少**，`searchBlob` 回填率 100%。

### 同步并发

前台轮询（20s）、写入防抖（1.5s）、用户手动点同步三者可能同时触发，
`NotesRepository.syncNow()` 用 `Mutex` 串行化，避免同一批 `dirty` 数据被并发重复提交。

### 其他加固

- **用户搜索防枚举**：`/api/users` 要求至少 2 个字符、改为用户名前缀匹配、单次最多返回 8 条。
  否则输入单个字母就能把全站账号翻出来（实测修复前 `q=a` 返回 10 个账号）。
- **删除标记回收**：`removed_notes` 会随使用无限增长拖慢同步。服务端启动时清理一次、
  之后每小时清理一次，只保留 30 天内的标记，单用户上限 5000 条。
- **游客笔记归属**：退出登录后再以游客身份写的笔记，只并入「刚刚退出的那个账号」，
  不会被下一个登录的人误认领（`adoptGuestNotes` + `last_user_id`）。

---

## 六、扩展性

- **新增笔记类型**：在 `NoteType` 增加枚举 → 在 `Models.Note` 增加字段 → 在 `NoteEntity` 与映射函数增加列 → 编辑器 `when` 增加分支 → **在 `AppDatabase` 补一条 `Migration`**。
- **新增同步字段**：服务端 `NOTE_FIELDS` 数组 + `noteValues()`，客户端 `ApiNote` 增加字段即可。
- **替换云服务**：`NotesRepository` 只依赖 `CloudApi` 的接口形态，换成 REST / gRPC 后端只需重写 `CloudApi`。

---

## 七、评分点对照

| 评分项 | 实现位置 |
| --- | --- |
| 1. 新建 / 编辑笔记 +35 | `ui/editor/EditorScreen.kt`、`EditorViewModel.kt` |
| 2. 笔记展示 / 搜索 +35 | `ui/home/HomeScreen.kt`、`SearchScreen.kt`、`components/NoteCard.kt` |
| 3. 笔记本地存储 +30 | `data/local/*`（Room）、`data/NotesRepository.kt` |
| 4. 增加笔记种类（每种 +20） | `domain/Models.kt` 中 5 种扩展类型 + 各自的编辑 / 展示形态 |
| 5. 笔记在线存储 +50 | `server/server.js`、`data/remote/CloudApi.kt`、增量同步 |
| 6. 多用户 +50 | 注册 / 登录 / 游客模式、数据分区（`accountId`） |
| 7. 多用户含协作 +100 | 分享（`/api/share`）、评论、协作方编辑与权限校验 |

---

## 八、验收自测

三层测试，答辩前可依次跑一遍。

```bash
# 1) JVM 单元测试（28 项，覆盖搜索匹配 / 权限 / 进度 / 类型解析 / 待同步标记）
./gradlew :app:testDebugUnitTest

# 2) 云服务端到端测试（43 项断言：认证 / 同步 / 6 种类型字段 / 多用户隔离 /
#    分享权限 / 只读拒绝 / 冲突检测 / 删除传播 / 评论 / 图片上传）
cd server && node server.js 8799
node tools/e2e-server-test.mjs http://127.0.0.1:8799

# 3) 本地 Room 数据库核查（拉到本地后看类型 / 分区 / 密码哈希 / 迁移结果）
adb shell "run-as com.nexo.smartnotes cp databases/smartnotes.db /sdcard/sn.db"
adb pull /sdcard/sn.db tools/sn.db
node tools/db-inspect.cjs tools/sn.db
node tools/migration-check.cjs tools/sn.db   # 版本号 / 索引 / 数据条数
node tools/searchblob-check.cjs tools/sn.db  # searchBlob 回填质量
```

实测结论：

- `assembleDebug` 构建通过；JVM 单测 **28/28** 通过。
- 云端 E2E **43/43** 通过（本地与公网隧道均已验证）。
- 新建日记 → 保存 → 强杀进程重启后数据仍在（本地持久化有效）。
- `alice` 作为协作者勾选清单后，变更写入云端 `notes.checklist`（协作闭环有效）。
- **v1 库升级到 v3：7 条笔记零丢失**，`searchBlob` 回填率 100%。
- 用户搜索枚举已封堵：修复前 `q=a` 返回 10 个账号，修复后要求 ≥2 字符且仅前缀匹配。

> 注：`POST /api/upload` 的协议是 JSON 字段 `dataBase64`（见 `CloudApi.uploadImage`），不是裸二进制 body。

---

## 九、答辩演示脚本

1. 游客模式新建一条「清单」，勾选几项，展示进度条。
2. 退出到「我的」→ 登录 `demo / demo123`，游客笔记自动并入账号并上传云端。
3. 打开「十一月的第一场雨」，点右上角分享，输入 `alice`，选择「可编辑」。
4. 退出登录，登录 `alice / alice123`，首页出现「来自 演示用户」的笔记，勾选清单项、发表评论。
5. 切回 `demo`，同步后可见 alice 的勾选与评论。
6. 展示搜索（输入 `Room` 命中清单条目）、日程视图、深色模式、回收站恢复。

---

## 十、说明

- 云服务为课程演示用途：明文 HTTP、token 无过期时间、无速率限制，生产环境需改用 HTTPS 与更严格的鉴权。
- 图片保存在应用私有目录，上传后以 `/uploads/<hash>.jpg` 形式在云端共享。
