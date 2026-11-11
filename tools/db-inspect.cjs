// 检查本项目 Room 数据库（只读）—— 课程项目自测用
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync(process.argv[2] || 'tools/sn.db');

const q = (sql, ...a) => { try { return db.prepare(sql).all(...a); } catch (e) { return [{ err: e.message }]; } };

console.log('=== 表 ===');
console.log(q("SELECT name FROM sqlite_master WHERE type='table'").map(r => r.name).join(', '));

console.log('\n=== 各类型笔记数 ===');
for (const r of q('SELECT type, COUNT(*) c FROM notes GROUP BY type')) console.log(`  ${r.type}: ${r.c}`);

console.log('\n=== 笔记明细 ===');
const cols = 'type, title, mood, weather, priority, startAt, checklistJson, attachmentsJson, dirty, serverSeq, shareRole, ownerName';
for (const r of q(`SELECT ${cols} FROM notes ORDER BY type, title`)) {
  console.log(`  [${r.type}] "${r.title}" mood=${r.mood} weather=${r.weather} prio=${r.priority} start=${r.startAt}`);
  console.log(`        checklist=${(r.checklistJson || '').slice(0, 70)}`);
  console.log(`        attach=${(r.attachmentsJson || '').slice(0, 60)} dirty=${r.dirty} seq=${r.serverSeq} shareRole=${r.shareRole} owner=${r.ownerName}`);
}

console.log('\n=== 评论 ===');
for (const r of q('SELECT authorName, text, noteId FROM comments')) console.log(`  ${r.authorName}: ${r.text}  (note ${r.noteId.slice(0, 8)})`);

console.log('\n=== 用户（密码已哈希） ===');
for (const r of q('SELECT username, displayName, length(passwordHash) hl, length(salt) sl, token IS NOT NULL tok, remoteId IS NOT NULL rid, lastSyncSeq FROM users')) {
  console.log(`  ${r.username} (${r.displayName}) hashLen=${r.hl} saltLen=${r.sl} token=${r.tok} remote=${r.rid} lastSyncSeq=${r.lastSyncSeq}`);
}

console.log('\n=== 数据分区（accountId） ===');
for (const r of q('SELECT accountId, COUNT(*) c FROM notes GROUP BY accountId')) console.log(`  ${r.accountId.slice(0, 12)}… : ${r.c} 条`);

console.log('\n=== 归档 / 回收站 ===');
const t = q('SELECT (SELECT COUNT(*) FROM notes WHERE trashed=1) t, (SELECT COUNT(*) FROM notes WHERE archived=1) a')[0];
console.log(`  trashed=${t.t} archived=${t.a}`);
