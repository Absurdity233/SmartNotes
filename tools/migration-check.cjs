// 校验 Room 迁移后数据是否完好（v1 -> v2）
const { DatabaseSync } = require('node:sqlite');
const path = process.argv[2] || 'tools/v2.db';
const db = new DatabaseSync(path);
const one = (sql) => { try { return db.prepare(sql).get(); } catch (e) { return { err: e.message }; } };
const all = (sql) => { try { return db.prepare(sql).all(); } catch (e) { return []; } };

const ver = one('PRAGMA user_version');
console.log(`${path}`);
console.log('  schema version :', ver.user_version);
console.log('  notes          :', one('SELECT COUNT(*) c FROM notes').c);
console.log('  users          :', one('SELECT COUNT(*) c FROM users').c);
console.log('  comments       :', one('SELECT COUNT(*) c FROM comments').c);
console.log('  notes 索引     :', all("SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='notes'").map(i => i.name).join(', '));
const t = all('SELECT type, title FROM notes ORDER BY type');
console.log('  笔记类型       :', t.map(x => x.type).join(', '));
console.log('  标题           :', t.map(x => x.title).filter(Boolean).join(' | '));
