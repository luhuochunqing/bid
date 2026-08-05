// Input: Playwright E2E + 后端 /api/auth/register + /api/projects + /api/projects/:id/evaluation/abandon + /api/notifications
// Output: 弃标通知回归测试 — /bidAdmin 收到通知 + admin 超级管理员被排除
// Pos: e2e/ - Playwright end-to-end coverage
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// 关联：PR !2267 — 通知接收人解析排除 admin 超级管理员（无 employee_number，不参与业务通知）
// 关联：PR !2271 — 修复 verify-notification-recipient-excludes-admin.sh 脚本（token 从 Set-Cookie 提取）
//
// 测试设计：
//   - 注册 admin 角色用户（替代真实 admin 账号，确保测试隔离性）
//   - 触发弃标通知后，admin 角色用户不应收到任何业务通知（PR !2267 核心断言）
//   - /bidAdmin 用户应收到弃标通知（正向验证）
//   - bid-TeamLeader（操作人）也应收到通知 — 验证 getProjectMemberUserIds(projectId, null)
//     不排除操作人的业务行为（参见 ProjectNotificationService.notifyAbandonBid）
//   - bid-Team 普通团队成员也应收到通知 — 验证项目成员侧的通知接收
//
// 接收人解析逻辑（ProjectNotificationService.notifyAbandonBid）：
//   recipientIds = teamMembers(不排除操作人) + getAdminUserIds()
//   getAdminUserIds() = NOTIFICATION_RECIPIENT_ROLES = [/bidAdmin, bid-TeamLeader, bid-SystemAdmin]
//   注：admin 不在 NOTIFICATION_RECIPIENT_ROLES 中（PR !2267 修复点）
//
// 测试前提：后端需运行包含 PR !2267 修复的代码（commit c54d67c71 及之后）。
// 在旧代码上本测试应失败（admin 收到弃标通知）— 这是回归测试的价值所在。

import { test, expect } from '@playwright/test'
import { apiBaseUrl, ensureApiSession } from './auth-helpers.js'

// 抽取项目创建逻辑（参考 project-evaluation-flow.spec.js）
async function createEvaluationProject(session, suffix, namePrefix, extraTeamMembers = []) {
  // 1. 创建标讯
  const tenderRes = await fetch(`${apiBaseUrl}/api/tenders`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      title: `${namePrefix} 标讯 ${suffix}`,
      source: 'E2E',
      budget: 100000,
      deadline: new Date(Date.now() + 30 * 86400000).toISOString().slice(0, 19),
      status: 'TRACKING'
    })
  })
  const tenderData = await tenderRes.json().catch(() => null)
  const tenderId = tenderData?.data?.id

  // 2. 创建项目
  const teamMembers = [session.user.id, ...extraTeamMembers.filter(id => id !== session.user.id)]
  const projRes = await fetch(`${apiBaseUrl}/api/projects`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${session.token}` },
    body: JSON.stringify({
      name: `${namePrefix} ${suffix}`,
      tenderId,
      status: 'BIDDING',
      managerId: session.user.id,
      teamMembers,
      startDate: new Date().toISOString().slice(0, 19),
      endDate: new Date(Date.now() + 90 * 86400000).toISOString().slice(0, 19)
    })
  })
  return projRes.json()
}

// 通用：带 Bearer token 调用 API
async function apiRequest(path, token, options = {}) {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      ...(options.headers || {})
    }
  })
  return { ok: response.ok, status: response.status, data: await response.json().catch(() => null) }
}

// 查询未读通知列表
async function fetchUnreadNotifications(token) {
  const result = await apiRequest(
    '/api/notifications?status=unread&page=0&size=50',
    token
  )
  if (!result.ok) {
    throw new Error(`fetch notifications failed: ${result.status}`)
  }
  const data = result.data?.data || {}
  return {
    total: data.totalElements || 0,
    items: data.content || data.items || []
  }
}

test.describe('§PR-2267 弃标通知 admin 排除回归测试', () => {
  test('弃标通知：/bidAdmin + bid-TeamLeader + bid-Team 收到 + admin 超级管理员被排除', async () => {
    const suffix = `${Date.now()}_${Math.random().toString(36).slice(2, 8)}`

    // ── 准备：注册 admin 角色用户（替代真实 admin 账号，确保测试隔离性）──
    // PR !2267 修复后，admin 不在 NOTIFICATION_RECIPIENT_ROLES 中，
    // 即使存在 roleCode='admin' 的用户，也不会被 getAdminUserIds() 选中
    const adminSession = await ensureApiSession({
      username: `e2e_abandon_superuser_${suffix}`,
      role: 'admin',
      fullName: 'E2E 超级管理员'
    })
    expect(adminSession.user.roleCode, 'admin 角色用户 roleCode 应为 admin').toBe('admin')

    // ── 准备：注册 /bidAdmin 用户作为通知接收人 ──
    // /bidAdmin 在 NOTIFICATION_RECIPIENT_ROLES 中，会被 getAdminUserIds() 选中
    const bidAdminSession = await ensureApiSession({
      username: `e2e_abandon_bidadmin_${suffix}`,
      role: '/bidAdmin',
      fullName: 'E2E 弃标通知接收人'
    })

    // ── 准备：注册 bid-TeamLeader 用户作为操作人（触发弃标）──
    // 选择 bid-TeamLeader 而非 /bidAdmin，是为了同时验证：
    //   1. bid-TeamLeader 在 NOTIFICATION_RECIPIENT_ROLES 中，会被 getAdminUserIds() 选中
    //   2. ProjectEvaluationController 的 @PreAuthorize 允许 BID_TEAMLEADER 触发弃标
    //   3. 操作人自身也会收到通知（getProjectMemberUserIds 第二参数为 null，不排除操作人）
    const leaderSession = await ensureApiSession({
      username: `e2e_abandon_leader_${suffix}`,
      role: 'bid-TeamLeader',
      fullName: 'E2E 弃标操作人'
    })

    // ── 准备：注册 bid-Team 普通成员，验证项目成员侧也收到通知 ──
    // bid-Team 不在 NOTIFICATION_RECIPIENT_ROLES 中，但作为项目 teamMembers 会被 getProjectMemberUserIds 选中
    const teamSession = await ensureApiSession({
      username: `e2e_abandon_team_${suffix}`,
      role: 'bid-Team',
      fullName: 'E2E 弃标项目成员'
    })

    // ── 准备：创建项目，把 /bidAdmin 加入 teamMembers（双保险）──
    // teamMembers 字段只写入 Project.teamMembers（ID 数组），不会自动同步到 project_member 表
    // 注意：/bidAdmin 已在 getAdminUserIds() 解析结果中，加入 teamMembers 是双保险，
    //       确保即使 getAdminUserIds() 出错，/bidAdmin 仍能通过 teamMembers 收到通知
    const projData = await createEvaluationProject(
      leaderSession,
      suffix,
      'E2E 弃标通知回归测试',
      [bidAdminSession.user.id]
    )
    expect(projData?.data?.id).toBeTruthy()
    const projectId = projData.data.id

    // ── 准备：通过 addMember API 把 bid-Team 加入 project_member 表 ──
    // 必须走 API 而非 Project.teamMembers 字段：getProjectMemberUserIds 查询的是
    // project_member 表（ProjectMember 实体），与 Project.teamMembers 字段无关
    // 用 admin 角色用户调用（有 ROLE_ADMIN，满足 @PreAuthorize hasAnyRole('ADMIN','MANAGER')）
    const addMemberResult = await apiRequest(
      `/api/projects/${projectId}/members`,
      adminSession.token,
      {
        method: 'POST',
        body: JSON.stringify({
          userId: teamSession.user.id,
          memberRole: 'TEAM_MEMBER',
          permissionLevel: 'VIEWER'
        })
      }
    )
    expect(addMemberResult.status, `addMember 应返回 200: ${addMemberResult.status}`).toBe(200)

    // ── Step 1: 记录各角色触发前的未读通知数 ──
    // admin 是新注册用户，baseline 应为 0；显式断言避免历史通知串扰导致测试不稳定
    const beforeAdmin = await fetchUnreadNotifications(adminSession.token)
    const adminUnreadBefore = beforeAdmin.total
    expect(
      adminUnreadBefore,
      'admin 新注册用户 baseline 应为 0（如有历史通知说明账号被复用，测试将不稳定）'
    ).toBe(0)

    const beforeBidAdmin = await fetchUnreadNotifications(bidAdminSession.token)
    const bidAdminUnreadBefore = beforeBidAdmin.total

    const beforeLeader = await fetchUnreadNotifications(leaderSession.token)
    const leaderUnreadBefore = beforeLeader.total

    const beforeTeam = await fetchUnreadNotifications(teamSession.token)
    const teamUnreadBefore = beforeTeam.total

    // ── Step 2: 操作人触发弃标通知 ──
    const abandonResult = await apiRequest(
      `/api/projects/${projectId}/evaluation/abandon`,
      leaderSession.token,
      {
        method: 'POST',
        body: JSON.stringify({
          reason: `E2E 回归测试 — PR !2267 admin 排除验证 ${suffix}`
        })
      }
    )
    // 显式断言 200，避免 201/204 等 2xx 误判
    expect(abandonResult.status, `abandon 应返回 200: ${abandonResult.status}`).toBe(200)

    // ── Step 3: 等待通知创建 ──
    // 通知通过 sendNotification 同步写入 DB，但走 Spring 事务提交后立即可查
    // 1500ms 等待是为了规避事务提交时序 + 索引刷新的微小延迟
    await new Promise(resolve => setTimeout(resolve, 1500))

    // ── Step 4: 验证 /bidAdmin 收到新通知（管理员侧正向验证）──
    const afterBidAdmin = await fetchUnreadNotifications(bidAdminSession.token)
    const bidAdminNewCount = afterBidAdmin.total - bidAdminUnreadBefore

    expect(
      bidAdminNewCount,
      `/bidAdmin 应收到新通知（before=${bidAdminUnreadBefore}, after=${afterBidAdmin.total}）`
    ).toBeGreaterThanOrEqual(1)

    // 最近的通知中应包含弃标通知
    const recentAbandonNotifs = afterBidAdmin.items.filter(
      n => (n.title || '').includes('弃标')
    )
    expect(
      recentAbandonNotifs.length,
      '/bidAdmin 最近通知中应包含弃标通知'
    ).toBeGreaterThanOrEqual(1)

    // ── Step 5: 验证 bid-TeamLeader（操作人）也收到通知 ──
    // 业务行为：getProjectMemberUserIds(projectId, null) 不排除操作人，
    // 操作人会收到自己触发的通知（参见 ProjectNotificationService.notifyAbandonBid 第 158 行）
    const afterLeader = await fetchUnreadNotifications(leaderSession.token)
    const leaderNewCount = afterLeader.total - leaderUnreadBefore
    expect(
      leaderNewCount,
      `bid-TeamLeader 操作人应收到自己触发的通知（before=${leaderUnreadBefore}, after=${afterLeader.total}）`
    ).toBeGreaterThanOrEqual(1)

    // ── Step 6: 验证 bid-Team 普通成员也收到通知（项目成员侧正向验证）──
    // 业务行为：项目团队成员（任意角色）都会被 getProjectMemberUserIds 选中
    const afterTeam = await fetchUnreadNotifications(teamSession.token)
    const teamNewCount = afterTeam.total - teamUnreadBefore
    expect(
      teamNewCount,
      `bid-Team 项目成员应收到通知（before=${teamUnreadBefore}, after=${afterTeam.total}）`
    ).toBeGreaterThanOrEqual(1)

    // ── Step 7: 验证 admin 角色用户未收到新通知（PR !2267 核心断言）──
    const afterAdmin = await fetchUnreadNotifications(adminSession.token)
    const adminNewCount = afterAdmin.total - adminUnreadBefore

    // 关键回归断言：admin 不应收到任何新通知
    expect(
      adminNewCount,
      `admin 超级管理员不应收到业务通知（PR !2267 修复）。before=${adminUnreadBefore}, after=${afterAdmin.total}`
    ).toBe(0)
  })
})
