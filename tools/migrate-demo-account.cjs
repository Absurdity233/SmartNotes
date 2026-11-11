// 把现有数据库里的 demo 账号迁移为 absurdity（保留笔记、分享、评论归属）
// 用法：node tools/migrate-demo-account.cjs [--dry-run]
const crypto = require('node:crypto');
const { DatabaseSync } = require('node:sqlite');

const db = new DatabaseSync('server/data/smartnotes.db');
const dryRun = process.argv.includes('--dry-run');

const hashPassword = (pw, salt) => crypto.scryptSync(pw, salt, 64).toString('hex');

const old = db.prepare('SELECT * FROM users WHERE username = ?').get('demo');
const exists = db.prepare('SELECT * FROM users WHERE username = ?').get('absurdity');

if (!old) {
  console.log('没有 demo 账号，无需迁移');
  if (exists) console.log('absurdity 已存在，可以直接使用');
  process.exit(0);
}
if (exists) {
  console.log('absurdity 已存在，跳过（避免覆盖）');
  process.exit(0);
}

const notes = db.prepare('SELECT COUNT(*) c FROM notes WHERE owner_id = ?').get(old.id).c;
const shares = db.prepare('SELECT COUNT(*) c FROM shares WHERE user_id = ?').get(old.id).c;
const comments = db.prepare('SELECT COUNT(*) c FROM comments WHERE user_id = ?').get(old.id).c;
console.log(`demo 名下：笔记 ${notes} 条、分享 ${shares} 条、评论 ${comments} 条`);

if (dryRun) {
  console.log('\n演练模式，未修改。去掉 --dry-run 执行。');
  process.exit(0);
}

const salt = crypto.randomBytes(16).toString('hex');
db.prepare(
  'UPDATE users SET username = ?, display_name = ?, password_hash = ?, salt = ? WHERE id = ?'
).run('absurdity', 'Absurdity', hashPassword('absurdity123', salt), salt, old.id);

// 旧 token 仍有效但属于这个用户 id，无需改动
console.log('已迁移：demo -> absurdity（密码 absurdity123）');
console.log('笔记 / 分享 / 评论归属均保持不变');

const after = db.prepare('SELECT username, display_name FROM users WHERE id = ?').get(old.id);
console.log(`当前：${after.username} | ${after.display_name}`);
