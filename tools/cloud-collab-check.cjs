// 云端协作链路核查（只读）
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync(process.argv[2] || 'server/data/smartnotes.db');
const q = (sql, ...a) => { try { return db.prepare(sql).all(...a); } catch (e) { return [{ err: e.message }]; } };

console.log('=== 表 ===');
console.log(q("SELECT name FROM sqlite_master WHERE type='table'").map(r => r.name).join(', '));

console.log('\n=== 用户 ===');
for (const r of q('SELECT id, username, display_name FROM users ORDER BY created_at'))
  console.log(`  ${r.username} | ${r.display_name} | ${r.id.slice(0, 8)}`);

console.log('\n=== 分享关系（谁把哪条笔记给了谁）===');
const rows = q(`SELECT s.note_id, s.permission, n.title, n.owner_id, u.username AS collaborator
                FROM shares s JOIN users u ON u.id = s.user_id
                LEFT JOIN notes n ON n.id = s.note_id`);
if (!rows.length) console.log('  （无）');
for (const r of rows) {
  const owner = q('SELECT username FROM users WHERE id = ?', r.owner_id)[0];
  console.log(`  「${r.title}」 owner=${owner ? owner.username : '?'} -> ${r.collaborator} (${r.permission})`);
}

console.log('\n=== 评论 ===');
const cs = q(`SELECT c.text, c.note_id, u.username, c.created_at FROM comments c
              JOIN users u ON u.id = c.user_id ORDER BY c.created_at`);
if (!cs.length) console.log('  （无）');
for (const r of cs) {
  const n = q('SELECT title FROM notes WHERE id = ?', r.note_id)[0];
  console.log(`  ${r.username} 在「${n ? n.title : '?'}」: ${r.text}`);
}

console.log('\n=== 多用户数据分区（每人的笔记数）===');
for (const r of q(`SELECT u.username, COUNT(n.id) AS c FROM users u
                   LEFT JOIN notes n ON n.owner_id = u.id AND n.deleted = 0
                   GROUP BY u.id ORDER BY u.username`))
  console.log(`  ${r.username}: ${r.c} 条`);

console.log('\n=== 协作笔记的同步状态 ===');
for (const r of q(`SELECT id, title, owner_id, seq, updated_at, checklist FROM notes
                   WHERE id IN (SELECT note_id FROM shares)`)) {
  const perms = q('SELECT u.username, s.permission FROM shares s JOIN users u ON u.id=s.user_id WHERE s.note_id=?', r.id);
  console.log(`  「${r.title}」 seq=${r.seq} updated=${new Date(r.updated_at).toISOString()}`);
  console.log(`     清单: ${(r.checklist || '').slice(0, 100)}`);
  console.log(`     共享: ${perms.map(p => p.username + '/' + p.permission).join(', ')}`);
}
