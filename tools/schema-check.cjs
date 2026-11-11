// 校验本轮修复涉及的数据库结构
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('server/data/smartnotes.db');
const q = (sql) => { try { return db.prepare(sql).all(); } catch (e) { return [{ err: e.message }]; } };

console.log('=== removed_notes 列 ===');
console.log(q('PRAGMA table_info(removed_notes)').map(c => `${c.name}:${c.type}`).join(', '));

console.log('\n=== removed_notes 索引 ===');
console.log(q("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='removed_notes'").map(i => i.name).join(', ') || '(无)');

console.log('\n=== 标记数量 ===');
console.log(q('SELECT COUNT(*) c FROM removed_notes')[0].c);

console.log('\n=== notes 索引 ===');
console.log(q("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='notes'").map(i => i.name).join(', '));

console.log('\n=== 数据完整性 ===');
const s = q('SELECT (SELECT COUNT(*) FROM users) u, (SELECT COUNT(*) FROM notes) n, (SELECT COUNT(*) FROM shares) sh, (SELECT COUNT(*) FROM comments) c')[0];
console.log(`users=${s.u} notes=${s.n} shares=${s.sh} comments=${s.c}`);
