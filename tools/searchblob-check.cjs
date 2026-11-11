// 校验 searchBlob 回填质量
const { DatabaseSync } = require('node:sqlite');
const db = new DatabaseSync(process.argv[2] || 'tools/v5.db');

const rows = db.prepare('SELECT title, searchBlob FROM notes').all();
console.log(`共 ${rows.length} 条\n`);
let empty = 0;
for (const r of rows) {
  const len = (r.searchBlob || '').length;
  if (len === 0) empty++;
  console.log(`[${len} 字符] ${r.title || '(无标题)'}`);
  console.log(`   ${(r.searchBlob || '').slice(0, 90).replace(/\n/g, ' ')}`);
}
console.log(`\n空 searchBlob: ${empty} 条`);

console.log('\n=== 搜索能力验证（模拟 DAO 的 LIKE 查询）===');
const probes = ['room', '杭州', '桂花', '买牛奶'];
for (const q of probes) {
  const hit = db
    .prepare('SELECT title FROM notes WHERE searchBlob LIKE ?')
    .all(`%${q}%`);
  console.log(`  "${q}" -> ${hit.length} 条: ${hit.map(h => h.title).join(', ') || '(无)'}`);
}
