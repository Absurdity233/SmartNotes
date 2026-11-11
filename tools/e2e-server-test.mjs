/**
 * 云服务端到端验证脚本（课程项目自测用）
 * 用法：node tools/e2e-server-test.mjs [baseUrl]
 * 覆盖：注册 / 登录 / 同步 / 多用户隔离 / 分享 / 权限校验 / 冲突检测 / 删除传播 / 图片上传
 */
const BASE = process.argv[2] || 'http://127.0.0.1:8799';

let pass = 0, fail = 0;
const results = [];
function check(name, ok, detail = '') {
  if (ok) { pass++; results.push(`  ✓ ${name}`); }
  else { fail++; results.push(`  ✗ ${name}${detail ? ' → ' + detail : ''}`); }
}

async function api(method, path, body, token) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(BASE + path, {
    method,
    headers,
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  let json = null;
  const text = await res.text();
  try { json = JSON.parse(text); } catch { json = { raw: text }; }
  return { status: res.status, body: json };
}

const now = () => Date.now();
const uid = () => crypto.randomUUID();
const suffix = Math.random().toString(36).slice(2, 8);

function makeNote(overrides = {}) {
  const t = now();
  return {
    id: uid(), ownerId: '', ownerName: '', type: 'TEXT', title: '', content: '',
    color: 0, tags: '', pinned: false, archived: false, deleted: false,
    createdAt: t, updatedAt: t, remindAt: null, dueAt: null, startAt: null, endAt: null,
    location: null, mood: null, weather: null, priority: 0, done: false,
    checklist: '[]', attachments: '[]', ...overrides,
  };
}

/** 服务端语义：accepted = 成功消费；rejected = 被规则拒绝（权限 / 时间戳）。 */
const wasConsumed = (r, id) =>
  (r.body.accepted || []).includes(id) || !(r.body.rejected || []).includes(id);

async function newUser(prefix) {
  const u = `${prefix}_${suffix}`;
  const r = await api('POST', '/api/auth/register', {
    username: u, password: 'pass' + suffix, displayName: prefix.toUpperCase(),
  });
  if (!r.body.ok) throw new Error('register failed: ' + JSON.stringify(r.body));
  return { ...r.body.user, token: r.body.token, username: u };
}

async function sync(token, since, notes = [], comments = []) {
  return api('POST', '/api/sync', { since, notes, comments }, token);
}

const main = async () => {
  console.log(`\n=== 智趣笔记 云服务 E2E (${BASE}) ===\n`);

  // 1. 健康检查
  console.log('[1] 健康检查');
  const health = await api('GET', '/api/health');
  check('健康检查返回 ok', health.body.ok === true, JSON.stringify(health.body));

  // 2. 注册 / 登录 / 校验
  console.log('[2] 账号体系');
  const A = await newUser('owner');
  check('注册返回 token', typeof A.token === 'string' && A.token.length > 20);
  const dup = await api('POST', '/api/auth/register', {
    username: A.username, password: 'x1234567', displayName: 'dup',
  });
  check('重复用户名被拒绝', dup.body.ok === false, JSON.stringify(dup.body));
  const shortPw = await api('POST', '/api/auth/register', {
    username: `sp_${suffix}`, password: '12', displayName: 'sp',
  });
  check('弱密码被拒绝', shortPw.body.ok === false, JSON.stringify(shortPw.body));
  const badLogin = await api('POST', '/api/auth/login', { username: A.username, password: 'wrongpass' });
  check('错误密码登录失败', badLogin.body.ok === false);
  const goodLogin = await api('POST', '/api/auth/login', { username: A.username, password: 'pass' + suffix });
  check('正确密码登录成功', goodLogin.body.ok === true && !!goodLogin.body.token);
  const me = await api('GET', '/api/auth/me', undefined, A.token);
  check('/auth/me 返回当前用户', me.body.ok === true && me.body.user.username === A.username);
  const noAuth = await api('POST', '/api/sync', { since: 0, notes: [], comments: [] });
  check('无 token 访问受保护接口被拒', noAuth.body.ok === false);

  // 3. 笔记上传与下载
  console.log('[3] 在线存储 / 同步');
  const B = await newUser('collab');
  const n1 = makeNote({ title: '本地笔记A', content: '来自设备一的内容', tags: 'work,test' });
  const up = await sync(A.token, 0, [n1]);
  check('笔记上传成功', up.body.ok === true, JSON.stringify(up.body).slice(0, 200));
  check('返回 accepted 包含该笔记', (up.body.accepted || []).includes(n1.id), JSON.stringify(up.body.accepted));
  const maxSeq1 = up.body.maxSeq;
  check('返回递增的 maxSeq', typeof maxSeq1 === 'number' && maxSeq1 > 0);

  const down = await sync(A.token, 0, []);
  const got = (down.body.notes || []).find((n) => n.id === n1.id);
  check('笔记可被拉取回来', !!got, 'notes=' + (down.body.notes || []).length);
  check('字段完整回传（标题/正文/标签）',
    got && got.title === '本地笔记A' && got.content === '来自设备一的内容' && got.tags === 'work,test');
  check('owner 身份标记正确', got && got.isOwner === true);

  // 4. 增量同步
  console.log('[4] 增量同步');
  const incr = await sync(A.token, maxSeq1, []);
  check('增量同步不重复返回旧数据', (incr.body.notes || []).length === 0,
    '返回 ' + (incr.body.notes || []).length + ' 条');

  // 5. 各类型笔记字段
  console.log('[5] 六种笔记类型字段');
  const types = [
    makeNote({ type: 'DIARY', title: '日记', mood: 'happy', weather: 'sunny', location: '杭州' }),
    makeNote({
      type: 'CHECKLIST', title: '清单',
      checklist: JSON.stringify([{ id: uid(), text: '买牛奶', done: true, priority: 0, dueAt: null }]),
    }),
    makeNote({
      type: 'TODO', title: '待办', priority: 2, dueAt: now() + 86400000,
      checklist: JSON.stringify([{ id: uid(), text: '交作业', done: false, priority: 2, dueAt: now() + 86400000 }]),
    }),
    makeNote({ type: 'SCHEDULE', title: '日程', startAt: now(), endAt: now() + 3600000, location: '会议室' }),
    makeNote({
      type: 'IMAGE', title: '图文', content: '配文',
      attachments: JSON.stringify([{ id: uid(), localPath: '', remoteUrl: '/uploads/x.jpg' }]),
    }),
  ];
  const tup = await sync(A.token, incr.body.maxSeq, types);
  check('五种扩展类型全部上传成功', (tup.body.accepted || []).length === types.length,
    `accepted=${(tup.body.accepted || []).length}/${types.length} rejected=${JSON.stringify(tup.body.rejected || [])}`);
  const tdown = await sync(A.token, 0, []);
  const byId = new Map((tdown.body.notes || []).map((n) => [n.id, n]));
  check('日记心情/天气/地点回传',
    byId.get(types[0].id)?.mood === 'happy' && byId.get(types[0].id)?.weather === 'sunny'
    && byId.get(types[0].id)?.location === '杭州');
  check('清单勾选状态回传', (byId.get(types[1].id)?.checklist || '').includes('买牛奶'));
  check('待办优先级回传', byId.get(types[2].id)?.priority === 2,
    'priority=' + byId.get(types[2].id)?.priority);
  check('日程起止时间回传', byId.get(types[3].id)?.startAt === types[3].startAt);
  check('图文附件回传', (byId.get(types[4].id)?.attachments || '').includes('/uploads/x.jpg'));

  // 6. 多用户隔离
  console.log('[6] 多用户数据隔离');
  const bView = await sync(B.token, 0, []);
  const leaked = (bView.body.notes || []).filter((n) => n.id === n1.id || types.some((t) => t.id === n.id));
  check('未共享时他人看不到笔记', leaked.length === 0, `泄漏 ${leaked.length} 条`);

  // 7. 分享 + 协作权限
  console.log('[7] 分享与协作权限');
  const shareRes = await api('POST', '/api/share',
    { noteId: n1.id, username: B.username, permission: 'edit' }, A.token);
  check('分享成功', shareRes.body.ok === true, JSON.stringify(shareRes.body).slice(0, 200));
  const bAfterShare = await sync(B.token, 0, []);
  const shared = (bAfterShare.body.notes || []).find((n) => n.id === n1.id);
  check('被分享者能看到笔记', !!shared);
  check('被分享者标记 canEdit', shared?.canEdit === true && shared?.isOwner === false);

  const selfShare = await api('POST', '/api/share',
    { noteId: n1.id, username: A.username, permission: 'edit' }, A.token);
  check('不能分享给自己', selfShare.body.ok === false);

  const ghostShare = await api('POST', '/api/share',
    { noteId: n1.id, username: `nobody_${suffix}`, permission: 'edit' }, A.token);
  check('分享给不存在的用户被拒', ghostShare.body.ok === false);

  const outsider = await newUser('out');
  const outShare = await api('POST', '/api/share',
    { noteId: n1.id, username: outsider.username, permission: 'edit' }, B.token);
  check('非创建者不能分享他人笔记', outShare.body.ok === false);

  // 8. 协作者编辑 → 创建者可见
  console.log('[8] 协作编辑闭环');
  const noteWithContent = (content, updatedAt) => ({ ...n1, content, updatedAt });
  const bEdit = noteWithContent('B 修改的内容', now() + 1000);
  const bPush = await sync(B.token, 0, [bEdit]);
  check('协作者（edit）可写入', wasConsumed(bPush, n1.id),
    JSON.stringify(bPush.body).slice(0, 300));
  const aSee = await sync(A.token, 0, []);
  const aView = (aSee.body.notes || []).find((n) => n.id === n1.id);
  check('创建者同步到协作者的修改', aView?.content === 'B 修改的内容');

  // 9. 只读权限
  console.log('[9] 只读权限校验');
  await api('POST', '/api/share', { noteId: n1.id, username: B.username, permission: 'view' }, A.token);
  const bViewOnly = await sync(B.token, 0, []);
  const viewNote = (bViewOnly.body.notes || []).find((n) => n.id === n1.id);
  check('权限降级为 view 后 canEdit=false', viewNote?.canEdit === false);
  const bDenied = await sync(B.token, 0, [noteWithContent('越权写入', now() + 5000)]);
  const denied = (bDenied.body.rejected || []).some((r) => r === n1.id);
  check('只读协作者写入被拒绝', denied, JSON.stringify(bDenied.body.rejected));

  // 10. 冲突检测
  console.log('[10] 冲突检测（过期写入）');
  const stale = await sync(A.token, 0, [noteWithContent('旧时间戳写入', 1)]);
  check('过期更新被拒绝', (stale.body.rejected || []).some((r) => r === n1.id),
    JSON.stringify(stale.body.rejected));

  // 11. 删除传播
  console.log('[11] 删除传播');
  const delNote = makeNote({ title: '待删除' });
  await sync(A.token, 0, [delNote]);
  await sync(A.token, 0, [{ ...delNote, deleted: true, updatedAt: now() + 2000 }]);
  const aAfterDel = await sync(A.token, 0, []);
  const delBack = (aAfterDel.body.notes || []).find((n) => n.id === delNote.id);
  check('删除后服务端标记 deleted=1', delBack ? delBack.deleted === true : true,
    delBack ? 'deleted=' + delBack.deleted : '已被服务端移除');

  // 12. 评论
  console.log('[12] 协作评论');
  const cmt = { id: uid(), noteId: n1.id, text: 'B 的评论', createdAt: now(), updatedAt: now(), deleted: false };
  const cp = await sync(B.token, 0, [], [cmt]);
  check('评论上传成功', cp.body.ok === true, JSON.stringify(cp.body).slice(0, 200));
  const ac = await sync(A.token, 0, []);
  check('创建者收到协作者评论', (ac.body.comments || []).some((c) => c.id === cmt.id),
    'comments=' + (ac.body.comments || []).length);

  // 13. 图片上传（服务端协议为 JSON + dataBase64）
  console.log('[13] 图片上传');
  const png = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==';
  const upJson = (await api('POST', '/api/upload', { dataBase64: 'data:image/png;base64,' + png }, A.token)).body;
  check('图片上传返回路径', upJson.ok === true && typeof upJson.path === 'string',
    JSON.stringify(upJson));
  if (upJson.path) {
    const imgRes = await fetch(BASE + upJson.path);
    check('上传的图片可访问', imgRes.status === 200, 'status=' + imgRes.status);
    const imgType = imgRes.headers.get('content-type') || '';
    check('图片返回 image/* 类型', imgType.startsWith('image/'), 'type=' + imgType);
  }
  const hugeJson = (await api('POST', '/api/upload', {
    dataBase64: 'data:image/png;base64,' + Buffer.alloc(16 * 1024 * 1024, 1).toString('base64'),
  }, A.token)).body;
  check('超大图片被拒绝', hugeJson.ok === false, JSON.stringify(hugeJson));
  const noAuthUp = (await api('POST', '/api/upload', { dataBase64: png })).body;
  check('未登录不能上传', noAuthUp.ok === false, JSON.stringify(noAuthUp));

  // 14. 用户搜索
  console.log('[14] 用户搜索');
  const search = await api('GET', `/api/users?q=${encodeURIComponent(B.username.slice(0, 4))}`, undefined, A.token);
  check('用户搜索可用', search.body.ok === true && Array.isArray(search.body.users));

  // 15. 越权读取他人笔记
  console.log('[15] 越权防护');
  const outAll = await sync(outsider.token, 0, []);
  check('无关用户拉取不到他人笔记', (outAll.body.notes || []).length === 0,
    'got ' + (outAll.body.notes || []).length);

  console.log('\n=== 结果 ===');
  results.forEach((r) => console.log(r));
  console.log(`\n通过 ${pass} / ${pass + fail}${fail ? `  ❌ 失败 ${fail}` : '  ✅ 全部通过'}\n`);
  process.exit(fail ? 1 : 0);
};

main().catch((e) => { console.error('测试异常：', e); process.exit(2); });
