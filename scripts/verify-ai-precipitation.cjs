// 4.1.2 AI 自动生成案例 端到端验证
// 路径：/project/:id (CLOSED tab) -> 🤖 AI自动生成案例按钮 -> 触发沉淀
// 验证：readiness 200 + 触发 200 + 案例入库 + 消息通知
const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

(async () => {
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();

  const consoleErrors = [];
  page.on('console', (msg) => {
    if (msg.type() === 'error') consoleErrors.push(`[${msg.type()}] ${msg.text()}`);
  });
  page.on('pageerror', (err) => consoleErrors.push(`[pageerror] ${err.message}`));
  page.on('requestfailed', (req) => consoleErrors.push(`[reqfail] ${req.url()} -> ${req.failure()?.errorText}`));
  page.on('response', (resp) => {
    if (resp.status() >= 400) consoleErrors.push(`[resp${resp.status()}] ${resp.url()}`);
  });

  const shots = path.resolve(__dirname, '../output/4.1.2-ai-precipitation');
  fs.mkdirSync(shots, { recursive: true });

  // ====== 1. login as bid_admin (看 AI 按钮需要的 role) ======
  console.log('=== 1. login bid_admin ===');
  await page.goto('http://127.0.0.1:1314/login', { waitUntil: 'networkidle' });
  await page.locator('input[placeholder*="请输入用户名"]').first().fill('bid_admin');
  await page.locator('input[placeholder*="请输入密码"]').first().fill('Test@123');
  // 等按钮 disabled 状态稳定
  await page.waitForTimeout(500);
  // 第一个 button 文本是 "登录"（不是 " 企业微信登录 "）。使用 text=
  await page.locator('button.login-button').first().click();
  await page.waitForLoadState('networkidle', { timeout: 15000 });
  await page.waitForTimeout(1500);
  const tokenAfterLogin = await page.evaluate(() => localStorage.getItem('token') || sessionStorage.getItem('token'));
  console.log('  token len:', tokenAfterLogin?.length || 0);
  await page.screenshot({ path: path.join(shots, '00-after-login.png'), fullPage: true });
  console.log('  current url:', page.url());

  // ====== 2. capture case count BEFORE ======
  console.log('=== 2. case count BEFORE ===');
  const beforeCases = await page.evaluate(async () => {
    const t = localStorage.getItem('token') || sessionStorage.getItem('token');
    const r = await fetch('/api/cases?page=0&size=200&sortBy=created', { headers: { Authorization: 'Bearer ' + t } });
    const data = await r.json();
    const list = data?.content || data?.data?.content || [];
    return { totalElements: data?.totalElements ?? data?.data?.totalElements ?? list.length, first3: list.slice(0, 3).map(c => ({ caseId: c.caseId, scoringTitle: c.scoringTitle, sourceProjectId: c.sourceProjectId, sourceProjectName: c.sourceProjectName })) };
  });
  console.log('  BEFORE:', JSON.stringify(beforeCases));

  // ====== 3. capture notifications BEFORE ======
  console.log('=== 3. notifications count BEFORE ===');
  const beforeNotif = await page.evaluate(async () => {
    const t = localStorage.getItem('token') || sessionStorage.getItem('token');
    const r = await fetch('/api/notifications?page=0&size=20', { headers: { Authorization: 'Bearer ' + t } });
    const data = await r.json();
    const list = data?.content || data?.records || data?.data?.content || [];
    return { total: data?.totalElements ?? data?.data?.totalElements ?? list.length, first: list.slice(0, 2) };
  });
  console.log('  BEFORE:', JSON.stringify(beforeNotif).slice(0, 400));

  // ====== 4. goto project 20 detail ======
  console.log('=== 4. goto /project/20 ===');
  await page.goto('http://127.0.0.1:1315/project/20', { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);
  await page.screenshot({ path: path.join(shots, '01-project-detail.png'), fullPage: true });

  // ====== 5. switch to CLOSED tab ======
  console.log('=== 5. switch to CLOSED tab ===');
  const closedTab = page.locator('.el-tabs__item:has-text("项目结项")');
  if (await closedTab.count() === 0) {
    console.log('  ❌ "项目结项" tab 未找到');
  } else {
    await closedTab.first().click();
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(shots, '02-closure-stage.png'), fullPage: true });
  }

  // ====== 6. find AI button ======
  console.log('=== 6. find 🤖 AI 自动生成案例 按钮 ===');
  const aiBtn = page.locator('button:has-text("AI自动生成案例")');
  const aiBtnCount = await aiBtn.count();
  console.log('  AI 按钮数量:', aiBtnCount);
  if (aiBtnCount === 0) {
    console.log('  ❌ AI 按钮不存在');
  } else {
    const disabled = await aiBtn.first().isDisabled();
    const visible = await aiBtn.first().isVisible();
    const html = await aiBtn.first().evaluate(el => el.outerHTML).catch(() => '');
    console.log('  AI 按钮 disabled:', disabled, 'visible:', visible);
    console.log('  HTML:', html.slice(0, 200));

    // 抓 tooltip 内容（hover）
    if (disabled) {
      await aiBtn.first().hover();
      await page.waitForTimeout(800);
      const tip = await page.locator('[role="tooltip"]').allTextContents();
      console.log('  tooltip:', tip);
    }
    await page.screenshot({ path: path.join(shots, '03-ai-button-state.png'), fullPage: true });
  }

  // ====== 7. click AI button (if enabled) ======
  if (aiBtnCount > 0 && !(await aiBtn.first().isDisabled())) {
    console.log('=== 7. click AI 按钮 ===');
    await aiBtn.first().click();
    // 等待弹窗 (ElMessage)
    await page.waitForTimeout(1500);
    const messages = await page.locator('.el-message').allTextContents();
    console.log('  ElMessage:', messages);
    await page.screenshot({ path: path.join(shots, '04-after-click.png'), fullPage: true });

    // 轮询等待异步任务完成
    console.log('=== 8. 轮询等案例入库 ===');
    let after = null;
    for (let i = 0; i < 12; i++) {
      await page.waitForTimeout(2500);
      const data = await page.evaluate(async () => {
        const t = localStorage.getItem('token') || sessionStorage.getItem('token');
        const r = await fetch('/api/cases?page=0&size=200&sortBy=created', { headers: { Authorization: 'Bearer ' + t } });
        const d = await r.json();
        const list = d?.content || d?.data?.content || [];
        return { total: d?.totalElements ?? d?.data?.totalElements ?? list.length, sample: list.slice(0, 3).map(c => ({ caseId: c.caseId, scoringTitle: c.scoringTitle, sourceProjectId: c.sourceProjectId, sourceProjectName: c.sourceProjectName, createdAt: c.createdAt })) };
      });
      console.log(`  poll #${i + 1} (${(i + 1) * 2.5}s) cases total: ${data.total}`);
      if (data.total > (beforeCases.totalElements || 0)) { after = data; break; }
    }
    console.log('  AFTER:', JSON.stringify(after).slice(0, 600));
    await page.screenshot({ path: path.join(shots, '05-after-poll.png'), fullPage: true });

    // ====== 9. 验证消息通知 ======
    console.log('=== 9. notifications AFTER ===');
    const afterNotif = await page.evaluate(async () => {
      const t = localStorage.getItem('token') || sessionStorage.getItem('token');
      const r = await fetch('/api/notifications?page=0&size=10', { headers: { Authorization: 'Bearer ' + t } });
      const d = await r.json();
      const list = d?.content || d?.records || d?.data?.content || [];
      return { total: d?.totalElements ?? d?.data?.totalElements ?? list.length, first: list.slice(0, 2) };
    });
    console.log('  AFTER notif:', JSON.stringify(afterNotif).slice(0, 500));
  } else {
    console.log('=== 7. skip click: AI 按钮 disabled 或不存在 ===');
    // 仍直接调 API 验证
    const direct = await page.evaluate(async () => {
      const t = localStorage.getItem('token') || sessionStorage.getItem('token');
      const r1 = await fetch('/api/cases/precipitation-readiness?projectId=20', { headers: { Authorization: 'Bearer ' + t } });
      const r2 = await fetch('/api/cases/precipitate?projectId=20', { method: 'POST', headers: { Authorization: 'Bearer ' + t } });
      return { readinessStatus: r1.status, readiness: await r1.text(), triggerStatus: r2.status, trigger: await r2.text() };
    });
    console.log('  direct API:', JSON.stringify(direct).slice(0, 600));
  }

  console.log('=== console errors ===');
  if (consoleErrors.length === 0) console.log('  (none)');
  else consoleErrors.forEach((e) => console.log('  ', e));

  await browser.close();
  console.log('=== DONE ===');
})().catch((e) => { console.error('FATAL:', e); process.exit(1); });
