// 查看云端最新写入的笔记（验证同步链路）
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('server/data/smartnotes.db');
const rows = db
  .prepare('SELECT seq, type, title, content, updated_at FROM notes ORDER BY seq DESC LIMIT 5')
  .all();
console.log('=== 云端最新 5 条 ===');
for (const r of rows) {
  console.log(`  seq=${r.seq} [${r.type}] "${r.title}" ${new Date(r.updated_at).toLocaleString('zh-CN')}`);
}
