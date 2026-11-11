// 清理压测/端到端测试产生的临时账号，只保留演示用的 absurdity 与 alice
// 用法：node tools/cleanup-test-users.cjs [--dry-run]
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync('server/data/smartnotes.db');
const dryRun = process.argv.includes('--dry-run');

const KEEP = new Set(['absurdity', 'alice']);
// 测试脚本生成的账号命名：owner_xxx / collab_xxx / out_xxx / bob_test / carol_test
const isTestUser = (u) => /^(owner|collab|out)_[a-z0-9]{5,}$/.test(u) || /_test$/.test(u);

const all = db.prepare('SELECT id, username FROM users').all();
const targets = all.filter((u) => !KEEP.has(u.username) && isTestUser(u.username));

console.log(`用户总数 ${all.length}，待清理 ${targets.length} 个${dryRun ? '（演练）' : ''}`);
for (const u of targets) console.log(`  - ${u.username}`);

if (!targets.length) {
  console.log('无需清理');
  process.exit(0);
}
if (dryRun) {
  console.log('\n这是演练，未做任何修改。去掉 --dry-run 真正执行。');
  process.exit(0);
}

const ids = targets.map((u) => u.id);
let removedNotes = 0, removedShares = 0, removedComments = 0, removedTokens = 0, removedUsers = 0;

for (const id of ids) {
  removedNotes += db.prepare('DELETE FROM notes WHERE owner_id = ?').run(id).changes;
  removedShares += db.prepare('DELETE FROM shares WHERE user_id = ?').run(id).changes;
  removedShares += db.prepare(
    'DELETE FROM shares WHERE note_id IN (SELECT id FROM notes WHERE owner_id = ?)'
  ).run(id).changes;
  removedComments += db.prepare('DELETE FROM comments WHERE user_id = ?').run(id).changes;
  removedTokens += db.prepare('DELETE FROM tokens WHERE user_id = ?').run(id).changes;
  db.prepare('DELETE FROM removed_notes WHERE user_id = ?').run(id);
  removedUsers += db.prepare('DELETE FROM users WHERE id = ?').run(id).changes;
}

// 清理孤儿数据
const orphans = db.prepare(
  'DELETE FROM notes WHERE owner_id NOT IN (SELECT id FROM users)'
).run().changes;

console.log(`\n已清理：用户 ${removedUsers}、笔记 ${removedNotes}、分享 ${removedShares}、评论 ${removedComments}、令牌 ${removedTokens}`);
if (orphans) console.log(`额外清理孤儿笔记 ${orphans} 条`);

const left = db.prepare('SELECT username FROM users ORDER BY created_at').all();
console.log(`剩余用户：${left.map((u) => u.username).join(', ')}`);
const n = db.prepare('SELECT COUNT(*) c FROM notes WHERE deleted = 0').get().c;
console.log(`剩余有效笔记：${n} 条`);
