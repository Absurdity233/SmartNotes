/**
 * 智趣笔记 · 云端服务
 * ------------------------------------------------------------
 * 零依赖 Node.js 服务（Node >= 22.5，使用内置 node:sqlite）。
 * 提供：账号注册 / 登录、笔记增量同步、协作分享、评论、图片上传。
 *
 * 启动：  node server.js            (默认端口 8787)
 *        node server.js 9000       (自定义端口)
 *        PORT=9000 node server.js
 */
'use strict';

const http = require('node:http');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');

const PORT = Number(process.env.PORT || process.argv[2] || 8787);
const ROOT = __dirname;
const DATA_DIR = path.join(ROOT, 'data');
const UPLOAD_DIR = path.join(ROOT, 'uploads');
fs.mkdirSync(DATA_DIR, { recursive: true });
fs.mkdirSync(UPLOAD_DIR, { recursive: true });

const db = new DatabaseSync(path.join(DATA_DIR, 'smartnotes.db'));
db.exec('PRAGMA journal_mode = WAL;');
db.exec(`
CREATE TABLE IF NOT EXISTS users (
  id TEXT PRIMARY KEY,
  username TEXT UNIQUE NOT NULL,
  display_name TEXT NOT NULL,
  password_hash TEXT NOT NULL,
  salt TEXT NOT NULL,
  avatar_color INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS tokens (
  token TEXT PRIMARY KEY,
  user_id TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE TABLE IF NOT EXISTS notes (
  id TEXT PRIMARY KEY,
  owner_id TEXT NOT NULL,
  type TEXT NOT NULL DEFAULT 'TEXT',
  title TEXT NOT NULL DEFAULT '',
  content TEXT NOT NULL DEFAULT '',
  color INTEGER NOT NULL DEFAULT 0,
  tags TEXT NOT NULL DEFAULT '',
  pinned INTEGER NOT NULL DEFAULT 0,
  archived INTEGER NOT NULL DEFAULT 0,
  deleted INTEGER NOT NULL DEFAULT 0,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  remind_at INTEGER,
  due_at INTEGER,
  start_at INTEGER,
  end_at INTEGER,
  location TEXT,
  mood TEXT,
  weather TEXT,
  priority INTEGER NOT NULL DEFAULT 0,
  done INTEGER NOT NULL DEFAULT 0,
  checklist TEXT NOT NULL DEFAULT '[]',
  attachments TEXT NOT NULL DEFAULT '[]',
  seq INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_notes_seq ON notes(seq);
CREATE INDEX IF NOT EXISTS idx_notes_owner ON notes(owner_id);
CREATE TABLE IF NOT EXISTS shares (
  note_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  permission TEXT NOT NULL DEFAULT 'view',
  created_at INTEGER NOT NULL,
  PRIMARY KEY (note_id, user_id)
);
CREATE TABLE IF NOT EXISTS comments (
  id TEXT PRIMARY KEY,
  note_id TEXT NOT NULL,
  user_id TEXT NOT NULL,
  text TEXT NOT NULL,
  created_at INTEGER NOT NULL,
  updated_at INTEGER NOT NULL,
  deleted INTEGER NOT NULL DEFAULT 0,
  seq INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_comments_note ON comments(note_id);
CREATE INDEX IF NOT EXISTS idx_comments_seq ON comments(seq);
CREATE TABLE IF NOT EXISTS removed_notes (
  user_id TEXT NOT NULL,
  note_id TEXT NOT NULL,
  seq INTEGER NOT NULL,
  created_at INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (user_id, note_id)
);
CREATE INDEX IF NOT EXISTS idx_removed_seq ON removed_notes(seq);
CREATE TABLE IF NOT EXISTS meta (
  key TEXT PRIMARY KEY,
  value INTEGER NOT NULL
);
INSERT OR IGNORE INTO meta(key, value) VALUES ('seq', 0);
`);

/* ------------------------------------------------------------------ utils */

const now = () => Date.now();
const uid = () => crypto.randomUUID();
const seqStmt = db.prepare("UPDATE meta SET value = value + 1 WHERE key = 'seq' RETURNING value");
const nextSeq = () => seqStmt.get().value;

function hashPassword(password, salt) {
  return crypto.scryptSync(password, salt, 64).toString('hex');
}

function makeUser(username, displayName, password) {
  const salt = crypto.randomBytes(16).toString('hex');
  const id = uid();
  db.prepare(
    'INSERT INTO users(id, username, display_name, password_hash, salt, avatar_color, created_at) VALUES (?,?,?,?,?,?,?)'
  ).run(id, username, displayName, hashPassword(password, salt), salt, 0xFF7C5CFF | 0, now());
  return db.prepare('SELECT * FROM users WHERE id = ?').get(id);
}

function userJson(u) {
  return {
    id: u.id,
    username: u.username,
    displayName: u.display_name,
    avatarColor: u.avatar_color,
  };
}

function issueToken(userId) {
  const token = crypto.randomBytes(32).toString('hex');
  db.prepare('INSERT INTO tokens(token, user_id, created_at) VALUES (?,?,?)').run(token, userId, now());
  return token;
}

function auth(req) {
  const header = req.headers['authorization'] || '';
  const token = header.startsWith('Bearer ') ? header.slice(7).trim() : '';
  if (!token) return null;
  return db.prepare('SELECT u.* FROM tokens t JOIN users u ON u.id = t.user_id WHERE t.token = ?').get(token) || null;
}

function readBody(req, limit = 40 * 1024 * 1024) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (c) => {
      size += c.length;
      if (size > limit) {
        reject(new Error('请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(c);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (e) {
        reject(new Error('JSON 解析失败'));
      }
    });
    req.on('error', reject);
  });
}

function json(res, code, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(code, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(body),
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
  });
  res.end(body);
}

function fail(res, code, message) {
  json(res, code, { ok: false, message });
}

/**
 * 删除标记（removed_notes）回收。
 *
 * 这张表记录「这条笔记已经从你的可见范围里移除」，客户端靠它做增量删除。
 * 若永不清理，它会随使用无限增长，同步响应越来越慢。
 * 策略：只保留最近 REMOVED_TTL_MS 内产生的标记 —— 超过这个窗口的客户端
 * 本来就要靠「首次全量拉取」重新对齐，不需要更早的墓碑。
 */
const REMOVED_TTL_MS = 30 * 24 * 60 * 60 * 1000; // 30 天
const REMOVED_MAX_PER_USER = 5000;

function collectRemovedNotes() {
  const cutoff = now() - REMOVED_TTL_MS;
  // created_at = 0 是旧库补列后的历史记录，按年龄无法判断，统一交给下面的数量上限处理
  const byAge = db
    .prepare('DELETE FROM removed_notes WHERE created_at > 0 AND created_at < ?')
    .run(cutoff);
  // 兜底：单个用户若积压过多，按 seq 保留最新的一批
  const heavy = db
    .prepare('SELECT user_id, COUNT(*) AS c FROM removed_notes GROUP BY user_id HAVING c > ?')
    .all(REMOVED_MAX_PER_USER);
  let byCount = 0;
  for (const row of heavy) {
    byCount += db
      .prepare(
        `DELETE FROM removed_notes WHERE user_id = ? AND seq NOT IN (
           SELECT seq FROM removed_notes WHERE user_id = ? ORDER BY seq DESC LIMIT ?
         )`
      )
      .run(row.user_id, row.user_id, REMOVED_MAX_PER_USER).changes;
  }
  const total = byAge.changes + byCount;
  if (total > 0) console.log(`[cleanup] 回收删除标记 ${total} 条`);
  return total;
}

/** 当前用户对某条笔记的权限：'owner' | 'edit' | 'view' | null */
function notePermission(noteId, userId) {
  const n = db.prepare('SELECT owner_id FROM notes WHERE id = ?').get(noteId);
  if (!n) return null;
  if (n.owner_id === userId) return 'owner';
  const s = db.prepare('SELECT permission FROM shares WHERE note_id = ? AND user_id = ?').get(noteId, userId);
  return s ? s.permission : null;
}

function sharesFor(noteId) {
  return db
    .prepare(
      `SELECT s.user_id, s.permission, u.username, u.display_name FROM shares s
       JOIN users u ON u.id = s.user_id WHERE s.note_id = ? ORDER BY s.created_at ASC`
    )
    .all(noteId)
    .map((s) => ({
      userId: s.user_id,
      username: s.username,
      displayName: s.display_name,
      permission: s.permission,
    }));
}

function noteJson(row, viewerId) {
  const owner = db.prepare('SELECT display_name FROM users WHERE id = ?').get(row.owner_id);
  const perm = notePermission(row.id, viewerId);
  return {
    id: row.id,
    ownerId: row.owner_id,
    ownerName: owner ? owner.display_name : '',
    type: row.type,
    title: row.title,
    content: row.content,
    color: row.color,
    tags: row.tags,
    pinned: !!row.pinned,
    archived: !!row.archived,
    deleted: !!row.deleted,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
    remindAt: row.remind_at,
    dueAt: row.due_at,
    startAt: row.start_at,
    endAt: row.end_at,
    location: row.location,
    mood: row.mood,
    weather: row.weather,
    priority: row.priority,
    done: !!row.done,
    checklist: row.checklist,
    attachments: row.attachments,
    seq: row.seq,
    canEdit: perm === 'owner' || perm === 'edit',
    isOwner: perm === 'owner',
    shares: perm ? sharesFor(row.id) : [],
  };
}

const NOTE_FIELDS = [
  'type', 'title', 'content', 'color', 'tags', 'pinned', 'archived', 'deleted',
  'created_at', 'updated_at', 'remind_at', 'due_at', 'start_at', 'end_at',
  'location', 'mood', 'weather', 'priority', 'done', 'checklist', 'attachments',
];

function noteValues(n) {
  return [
    String(n.type || 'TEXT'),
    String(n.title || ''),
    String(n.content || ''),
    Number(n.color || 0),
    String(n.tags || ''),
    n.pinned ? 1 : 0,
    n.archived ? 1 : 0,
    n.deleted ? 1 : 0,
    Number(n.createdAt || now()),
    Number(n.updatedAt || now()),
    n.remindAt == null ? null : Number(n.remindAt),
    n.dueAt == null ? null : Number(n.dueAt),
    n.startAt == null ? null : Number(n.startAt),
    n.endAt == null ? null : Number(n.endAt),
    n.location == null ? null : String(n.location),
    n.mood == null ? null : String(n.mood),
    n.weather == null ? null : String(n.weather),
    Number(n.priority || 0),
    n.done ? 1 : 0,
    typeof n.checklist === 'string' ? n.checklist : JSON.stringify(n.checklist || []),
    typeof n.attachments === 'string' ? n.attachments : JSON.stringify(n.attachments || []),
  ];
}

/* ------------------------------------------------------------------ server */

const server = http.createServer(async (req, res) => {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const pathname = url.pathname;
  const method = req.method || 'GET';

  if (method === 'OPTIONS') {
    res.writeHead(204, {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
      'Access-Control-Allow-Methods': 'GET, POST, DELETE, OPTIONS',
    });
    return res.end();
  }

  try {
    /* ---------------- 健康检查 & 首页 ---------------- */
    if (method === 'GET' && (pathname === '/api/health' || pathname === '/health')) {
      const users = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
      const notes = db.prepare('SELECT COUNT(*) AS c FROM notes WHERE deleted = 0').get().c;
      return json(res, 200, { ok: true, service: 'smart-notes-cloud', version: '1.0.0', users, notes, time: now() });
    }

    if (method === 'GET' && pathname === '/') {
      const users = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
      const notes = db.prepare('SELECT COUNT(*) AS c FROM notes WHERE deleted = 0').get().c;
      const html = `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>智趣笔记 · 云服务</title>
      <body style="font-family:system-ui,-apple-system,'Segoe UI',sans-serif;background:#faf9fc;color:#1f1b2e;display:flex;min-height:100vh;align-items:center;justify-content:center;margin:0">
      <div style="background:#fff;padding:44px 60px;border-radius:28px;box-shadow:0 24px 70px rgba(80,60,160,.16);text-align:center;max-width:420px">
      <div style="font-size:44px">📝</div><h1 style="margin:10px 0 6px;font-size:26px">智趣笔记 · 云服务</h1>
      <p style="color:#6b6480;margin:4px 0">服务运行中 · 用户 <b>${users}</b> · 笔记 <b>${notes}</b></p>
      <p style="color:#9b94ad;font-size:13px;margin-top:18px">Smart Notes Cloud · v1.0.0 · ${new Date().toLocaleString('zh-CN')}</p>
      </div></body></html>`;
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8' });
      return res.end(html);
    }

    /* ---------------- 认证 ---------------- */
    if (method === 'POST' && pathname === '/api/auth/register') {
      const body = await readBody(req);
      const username = String(body.username || '').trim().toLowerCase();
      const password = String(body.password || '');
      const displayName = String(body.displayName || body.username || '').trim() || username;
      if (!/^[a-z0-9_]{3,20}$/.test(username)) return fail(res, 400, '用户名需为 3-20 位字母、数字或下划线');
      if (password.length < 6) return fail(res, 400, '密码至少 6 位');
      if (db.prepare('SELECT id FROM users WHERE username = ?').get(username)) {
        return fail(res, 409, '该用户名已被注册');
      }
      const user = makeUser(username, displayName.slice(0, 24), password);
      return json(res, 200, { ok: true, token: issueToken(user.id), user: userJson(user) });
    }

    if (method === 'POST' && pathname === '/api/auth/login') {
      const body = await readBody(req);
      const username = String(body.username || '').trim().toLowerCase();
      const password = String(body.password || '');
      const user = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
      if (!user) return fail(res, 401, '用户名或密码错误');
      const hash = hashPassword(password, user.salt);
      const ok =
        hash.length === user.password_hash.length &&
        crypto.timingSafeEqual(Buffer.from(hash), Buffer.from(user.password_hash));
      if (!ok) return fail(res, 401, '用户名或密码错误');
      return json(res, 200, { ok: true, token: issueToken(user.id), user: userJson(user) });
    }

    if (method === 'GET' && pathname === '/api/auth/me') {
      const me = auth(req);
      if (!me) return fail(res, 401, '登录已失效，请重新登录');
      return json(res, 200, { ok: true, user: userJson(me) });
    }

    /* ---------------- 用户搜索（用于分享） ---------------- */
    if (method === 'GET' && pathname === '/api/users') {
      const me = auth(req);
      if (!me) return fail(res, 401, '未登录');
      const q = String(url.searchParams.get('q') || '').trim().toLowerCase();
      // 至少 2 个字符：否则单字符查询可把全站账号一次性枚举出来
      if (q.length < 2) {
        return json(res, 200, {
          ok: true,
          users: [],
          message: q.length === 1 ? '请至少输入 2 个字符' : '',
        });
      }
      // 只做「前缀匹配」而不是任意位置包含，并限制返回条数：
      // 既能满足「输入用户名找人分享」的实际用途，又避免把用户表当通讯录。
      const like = `${q}%`;
      const rows = db
        .prepare(
          `SELECT * FROM users
           WHERE (username LIKE ? OR display_name LIKE ?) AND id != ?
           ORDER BY username LIMIT 8`
        )
        .all(like, like, me.id);
      return json(res, 200, { ok: true, users: rows.map(userJson) });
    }

    /* ---------------- 分享 ---------------- */
    if (method === 'POST' && pathname === '/api/share') {
      const me = auth(req);
      if (!me) return fail(res, 401, '未登录');
      const body = await readBody(req);
      const noteId = String(body.noteId || '');
      const username = String(body.username || '').trim().toLowerCase();
      const permission = body.permission === 'edit' ? 'edit' : 'view';
      const note = db.prepare('SELECT * FROM notes WHERE id = ?').get(noteId);
      if (!note) return fail(res, 404, '笔记不存在');
      if (note.owner_id !== me.id) return fail(res, 403, '只有创建者可以分享该笔记');
      const target = db.prepare('SELECT * FROM users WHERE username = ?').get(username);
      if (!target) return fail(res, 404, '用户不存在');
      if (target.id === me.id) return fail(res, 400, '不能分享给自己');
      db.prepare(
        `INSERT INTO shares(note_id, user_id, permission, created_at) VALUES (?,?,?,?)
         ON CONFLICT(note_id, user_id) DO UPDATE SET permission = excluded.permission`
      ).run(noteId, target.id, permission, now());
      db.prepare('UPDATE notes SET seq = ? WHERE id = ?').run(nextSeq(), noteId);
      db.prepare('DELETE FROM removed_notes WHERE user_id = ? AND note_id = ?').run(target.id, noteId);
      return json(res, 200, { ok: true, shares: sharesFor(noteId) });
    }

    if (method === 'DELETE' && pathname.startsWith('/api/share/')) {
      const me = auth(req);
      if (!me) return fail(res, 401, '未登录');
      const parts = pathname.split('/').filter(Boolean); // api share :noteId :userId
      const noteId = parts[2];
      const targetUserId = parts[3];
      const note = db.prepare('SELECT * FROM notes WHERE id = ?').get(noteId);
      if (!note) return fail(res, 404, '笔记不存在');
      if (note.owner_id !== me.id) return fail(res, 403, '只有创建者可以取消分享');
      db.prepare('DELETE FROM shares WHERE note_id = ? AND user_id = ?').run(noteId, targetUserId);
      const seq = nextSeq();
      db.prepare('UPDATE notes SET seq = ? WHERE id = ?').run(seq, noteId);
      db.prepare(
        `INSERT INTO removed_notes(user_id, note_id, seq, created_at) VALUES (?,?,?,?)
         ON CONFLICT(user_id, note_id) DO UPDATE SET seq = excluded.seq, created_at = excluded.created_at`
      ).run(targetUserId, noteId, seq, now());
      return json(res, 200, { ok: true, shares: sharesFor(noteId) });
    }

    /* ---------------- 图片上传 ---------------- */
    if (method === 'POST' && pathname === '/api/upload') {
      const me = auth(req);
      if (!me) return fail(res, 401, '未登录');
      const body = await readBody(req);
      const base64 = String(body.dataBase64 || '');
      if (!base64) return fail(res, 400, '缺少图片数据');
      const raw = base64.replace(/^data:[^,]*,/, '');
      const data = Buffer.from(raw, 'base64');
      if (data.length > 15 * 1024 * 1024) return fail(res, 413, '图片过大（上限 15MB）');
      // 以文件头为准，避免客户端声明的 mime 与实际内容不符
      let ext = 'jpg';
      if (data.length > 8 && data[0] === 0x89 && data[1] === 0x50) ext = 'png';
      else if (data.length > 12 && data[8] === 0x57 && data[9] === 0x45) ext = 'webp';
      else if (data.length > 6 && data[0] === 0x47) ext = 'gif';
      const name = `${crypto.randomBytes(16).toString('hex')}.${ext}`;
      fs.writeFileSync(path.join(UPLOAD_DIR, name), data);
      return json(res, 200, { ok: true, path: `/uploads/${name}` });
    }

    /* ---------------- 增量同步 ---------------- */
    if (method === 'POST' && pathname === '/api/sync') {
      const me = auth(req);
      if (!me) return fail(res, 401, '登录已失效，请重新登录');
      const body = await readBody(req);
      const since = Number(body.since || 0);
      const incomingNotes = Array.isArray(body.notes) ? body.notes : [];
      const incomingComments = Array.isArray(body.comments) ? body.comments : [];
      const accepted = [];
      const rejected = [];

      for (const n of incomingNotes) {
        const id = String(n.id || '');
        if (!id) continue;
        const existing = db.prepare('SELECT * FROM notes WHERE id = ?').get(id);
        if (!existing) {
          if (n.ownerId && n.ownerId !== me.id) {
            rejected.push(id);
            continue;
          }
          const seq = nextSeq();
          const values = noteValues(n);
          db.prepare(
            `INSERT INTO notes(id, owner_id, ${NOTE_FIELDS.join(', ')}, seq) VALUES (?,?,${NOTE_FIELDS.map(() => '?').join(',')},?)`
          ).run(id, me.id, ...values, seq);
          accepted.push(id);
          continue;
        }
        const perm = notePermission(id, me.id);
        if (perm !== 'owner' && perm !== 'edit') {
          rejected.push(id);
          continue;
        }
        if (Number(n.updatedAt || 0) < existing.updated_at) {
          rejected.push(id);
          continue;
        }
        const seq = nextSeq();
        db.prepare(
          `UPDATE notes SET ${NOTE_FIELDS.map((f) => `${f} = ?`).join(', ')}, seq = ? WHERE id = ?`
        ).run(...noteValues(n), seq, id);
        accepted.push(id);
      }

      for (const c of incomingComments) {
        const id = String(c.id || '');
        const noteId = String(c.noteId || '');
        if (!id || !noteId) continue;
        if (!notePermission(noteId, me.id)) continue;
        const existing = db.prepare('SELECT * FROM comments WHERE id = ?').get(id);
        if (!existing) {
          const seq = nextSeq();
          db.prepare(
            'INSERT INTO comments(id, note_id, user_id, text, created_at, updated_at, deleted, seq) VALUES (?,?,?,?,?,?,?,?)'
          ).run(
            id, noteId, me.id, String(c.text || ''),
            Number(c.createdAt || now()), Number(c.updatedAt || now()),
            c.deleted ? 1 : 0, seq
          );
        } else if (existing.user_id === me.id && Number(c.updatedAt || 0) >= existing.updated_at) {
          const seq = nextSeq();
          db.prepare('UPDATE comments SET text = ?, updated_at = ?, deleted = ?, seq = ? WHERE id = ?')
            .run(String(c.text || ''), Number(c.updatedAt || now()), c.deleted ? 1 : 0, seq, id);
        }
      }

      const notes = db
        .prepare(
          `SELECT * FROM notes
           WHERE seq > ? AND (owner_id = ? OR id IN (SELECT note_id FROM shares WHERE user_id = ?))
           ORDER BY seq ASC LIMIT 5000`
        )
        .all(since, me.id, me.id)
        .map((row) => noteJson(row, me.id));

      const comments = db
        .prepare(
          `SELECT c.*, u.display_name AS author_name FROM comments c
           JOIN users u ON u.id = c.user_id
           WHERE c.seq > ? AND c.note_id IN (
             SELECT id FROM notes WHERE owner_id = ? OR id IN (SELECT note_id FROM shares WHERE user_id = ?)
           ) ORDER BY c.seq ASC LIMIT 5000`
        )
        .all(since, me.id, me.id)
        .map((c) => ({
          id: c.id,
          noteId: c.note_id,
          userId: c.user_id,
          authorName: c.author_name,
          text: c.text,
          createdAt: c.created_at,
          updatedAt: c.updated_at,
          deleted: !!c.deleted,
          seq: c.seq,
        }));

      const removed = db
        .prepare('SELECT note_id FROM removed_notes WHERE user_id = ? AND seq > ?')
        .all(me.id, since)
        .map((r) => r.note_id);

      const maxSeq = db.prepare("SELECT value FROM meta WHERE key = 'seq'").get().value;

      return json(res, 200, {
        ok: true,
        serverTime: now(),
        maxSeq,
        accepted,
        rejected,
        notes,
        comments,
        removed,
        user: userJson(me),
      });
    }

    /* ---------------- 静态图片 ---------------- */
    if (method === 'GET' && pathname.startsWith('/uploads/')) {
      const name = path.basename(pathname);
      const file = path.join(UPLOAD_DIR, name);
      if (!fs.existsSync(file)) return fail(res, 404, '文件不存在');
      const ext = path.extname(name).toLowerCase();
      const types = {
        '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png',
        '.webp': 'image/webp', '.gif': 'image/gif',
      };
      res.writeHead(200, {
        'Content-Type': types[ext] || 'application/octet-stream',
        'Cache-Control': 'public, max-age=86400',
      });
      return fs.createReadStream(file).pipe(res);
    }

    return fail(res, 404, '接口不存在');
  } catch (err) {
    console.error('[error]', err);
    return fail(res, 500, err.message || '服务器内部错误');
  }
});

/* ------------------------------------------------------------------ 演示数据 */

function seed() {
  const count = db.prepare('SELECT COUNT(*) AS c FROM users').get().c;
  if (count > 0) return;
  const demo = makeUser('demo', '演示用户', 'demo123');
  const alice = makeUser('alice', '小艾', 'alice123');
  const t = now();

  const samples = [
    {
      type: 'DIARY', title: '十一月的第一场雨', color: 3,
      content:
        '窗外下起了入秋以来的第一场雨，空气里都是桂花和泥土的味道。\n今天把项目的主体框架搭好了，虽然还有很多细节要打磨，但看着它一点点成形，心里很踏实。\n\n愿明天也是温柔的一天。',
      mood: '😌', weather: '🌧️', tags: '日记,秋天', pinned: 1,
    },
    {
      type: 'CHECKLIST', title: '期末项目冲刺清单', color: 2,
      tags: '课程,项目',
      checklist: [
        { id: 'c1', text: '完成笔记的新建 / 编辑', done: true },
        { id: 'c2', text: '完成笔记展示与搜索', done: true },
        { id: 'c3', text: '完成本地存储 (Room)', done: true },
        { id: 'c4', text: '接入云端同步', done: true },
        { id: 'c5', text: '实现多用户与协作', done: true },
        { id: 'c6', text: '打磨界面细节与动效', done: false },
        { id: 'c7', text: '准备答辩演示', done: false },
      ],
    },
    {
      type: 'SCHEDULE', title: '小组答辩 · 教室 A302', color: 6,
      content: '提前 20 分钟到场调试设备，记得带上演示手机和充电宝。',
      tags: '答辩', location: '教学楼 A302',
      startAt: t + 2 * 86400_000, endAt: t + 2 * 86400_000 + 3600_000,
      remindAt: t + 2 * 86400_000 - 1800_000,
    },
    {
      type: 'TODO', title: '把设计稿同步给组员', color: 8,
      content: '导出 Figma 最新版本，附上交互说明。',
      tags: '协作', dueAt: t + 86400_000, priority: 2,
      checklist: [{ id: 't1', text: '导出设计稿', done: false, priority: 2, dueAt: t + 86400_000 }],
    },
    {
      type: 'TEXT', title: '灵感碎片', color: 1,
      content: '笔记应用真正打动人的地方，不是功能有多少，而是它让人愿意每天打开一次。\n\n—— 记于图书馆四楼',
      tags: '灵感',
    },
  ];

  for (const s of samples) {
    const id = uid();
    const seq = nextSeq();
    const values = noteValues({ ...s, createdAt: t - 86_400_000, updatedAt: t - 3_600_000 });
    db.prepare(
      `INSERT INTO notes(id, owner_id, ${NOTE_FIELDS.join(', ')}, seq) VALUES (?,?,${NOTE_FIELDS.map(() => '?').join(',')},?)`
    ).run(id, demo.id, ...values, seq);
    if (s.type === 'CHECKLIST') {
      db.prepare('INSERT INTO shares(note_id, user_id, permission, created_at) VALUES (?,?,?,?)')
        .run(id, alice.id, 'edit', now());
    }
  }
  console.log('[seed] 演示账号：demo / demo123，alice / alice123');
}

seed();

/**
 * 轻量级列迁移。
 *
 * `CREATE TABLE IF NOT EXISTS` 对已存在的表不生效，所以给旧库补列必须显式做。
 * 这里用 PRAGMA 检查后再 ALTER，能同时兼容「新装」与「已有数据」两种库。
 */
function ensureColumn(table, column, definition) {
  const cols = db.prepare(`PRAGMA table_info(${table})`).all();
  if (!cols.some((c) => c.name === column)) {
    db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
    console.log(`[migrate] ${table}.${column} 已补充`);
  }
}
ensureColumn('removed_notes', 'created_at', 'INTEGER NOT NULL DEFAULT 0');

// 启动时清理一次，之后每小时一次，避免删除标记无限累积
collectRemovedNotes();
setInterval(collectRemovedNotes, 60 * 60 * 1000).unref();

server.listen(PORT, '0.0.0.0', () => {
  console.log('');
  console.log('  智趣笔记 · 云服务已启动');
  console.log(`  地址:      http://0.0.0.0:${PORT}`);
  console.log(`  健康检查:  http://127.0.0.1:${PORT}/api/health`);
  console.log(`  模拟器:    http://10.0.2.2:${PORT}`);
  console.log('');
});
