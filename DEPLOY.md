# 部署到公网服务器

服务端是零依赖 Node 程序，`server.js` 一个文件加 `package.json` 就能跑。

## 前置：你需要准备什么

1. 一个 Render 账号（GitHub 账号直接登录，不需要信用卡）
2. 代码已经在 GitHub 上（`Absurdity233/SmartNotes`，已完成）

## Render 部署（约 5 分钟）

### 1. 创建服务

打开 <https://dashboard.render.com/blueprints>，点 **New Blueprint Instance**，
选择 `Absurdity233/SmartNotes` 仓库。

仓库根目录的 `render.yaml` 会被自动识别，无需手填任何参数。

### 2. 等它部署完

首次部署约 2-3 分钟。状态变成 **Live** 后，页面顶部会显示你的地址：

```
https://smartnotes-cloud-xxxx.onrender.com
```

### 3. 验证

```bash
curl https://smartnotes-cloud-xxxx.onrender.com/api/health
```

应返回：

```json
{"ok":true,"service":"smart-notes-cloud","version":"1.0.0","users":2,"notes":5,...}
```

`users:2` 说明种子数据已写入（`absurdity` 和 `alice`）。

### 4. 把地址编进 APK

```bash
./gradlew :app:assembleDebug -Psmartnotes.server=https://smartnotes-cloud-xxxx.onrender.com
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

装到手机上就能直接用，登录 `absurdity / absurdity123`。

---

## 关于数据持久化（重要）

Render 的**免费实例没有持久化磁盘**。会发生什么：

| 情况 | 数据 |
| --- | --- |
| 服务运行中 | 正常读写 |
| 15 分钟无请求后休眠 | 保留（同一实例） |
| 重新部署 / 实例被回收 | **丢失，回到种子状态** |

对答辩演示够用（演示期间不会重启）。要长期保存数据，改成付费方案：

1. Render 后台把服务 plan 改成 **Starter**（约 $7/月）
2. 取消 `render.yaml` 里 `disk:` 段的注释，重新部署

改完数据会落在 `/var/data`，容器重建也不丢。

---

## 免费实例的冷启动

15 分钟没有请求会休眠，下次访问要等 **约 30 秒**唤醒。

**答辩前 1 分钟先访问一次** `/api/health` 把它叫醒，否则老师看到的是白屏加载中。

如果想让它常驻，用 UptimeRobot 之类的监控每 10 分钟 ping 一次 `/api/health`（免费）。

---

## 其他平台

`server/Dockerfile` 是通用的。任何支持容器的平台都能用：

```bash
cd server
docker build -t smartnotes-cloud .
docker run -d -p 8787:8787 -v /your/data:/data smartnotes-cloud
```

Fly.io 用 `server/fly.toml`，但免费额度已收紧，部署前先确认账户计费状态。

## 环境变量

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `PORT` | `8787` | 监听端口，平台通常会自动注入 |
| `SMARTNOTES_DATA_DIR` | 项目目录 | 数据库与上传图片的存放位置 |

## 上线后建议改的东西

当前服务端是课程演示级别的，公网长期运行前至少改这两处：

1. **token 不过期** —— `tokens` 表没有过期时间，泄露就是永久有效
2. **没有速率限制** —— 注册和登录接口可以被暴力尝试

代码位置：`server/server.js` 的 `issueToken()` 与各 `/api/auth/*` 处理分支。
