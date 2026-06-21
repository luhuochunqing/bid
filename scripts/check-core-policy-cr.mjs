#!/usr/bin/env node
// Input: backend/src/main/java/**/*Service.java and *AppService.java
// Output: warning when authorization logic is implemented in Service/AppService instead of core/*Policy
// Pos: scripts/ - Pure core policy CR enforcement helper (2026-06-21 CO-290 复盘)
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。
//
// Background:
//   CO-290 中 ProjectDraftingService.submitBid() 直接比较 role code 做权限判定，
//   违反 FP-Java 架构中 "权限/授权逻辑必须下沉到纯核心 Policy 类" 的约定。
//   本检查用于 Code Review 阶段提醒：Service/AppService 只应做编排，
//   真正的授权决策应委托给 core/*Policy。
//
// 规则（warning only，非阻断）：
//   1. Service/AppService 中直接引用 RoleProfileCatalog / RoleProfile 并进行角色比较/判断时告警。
//   2. Service/AppService 中抛出 HttpStatus.FORBIDDEN，但未调用任何 core *Policy.canXxx 方法时告警。
//
// 限制：静态启发式检查，部分角色查询类 Service（如 RoleProfileService/AuthService）已加入白名单。

import fs from 'node:fs';
import path from 'node:path';
import { spawnSync } from 'node:child_process';

const gitEnv = { ...process.env };
delete gitEnv.GIT_DIR;
delete gitEnv.GIT_WORK_TREE;

const ROOT_DIR = (() => {
  const res = spawnSync('git', ['rev-parse', '--show-toplevel'], { encoding: 'utf8', env: gitEnv });
  return res.stdout ? res.stdout.trim() : process.cwd();
})();

const STAGED_ONLY = process.argv.includes('--staged');
const BACKEND_DIR = path.join(ROOT_DIR, 'backend/src/main/java/com/xiyu/bid');

const SKIP_DIRS = new Set(['core', 'test', 'target']);

// 明确允许处理角色的服务类（查询/转换，不做授权决策）
const WHITELIST_FILES = new Set([
  'RoleProfileService.java',
  'AuthService.java',
  'UserDetailsServiceImpl.java',
  'ProjectAccessScopeService.java',
  'RoleProfileCatalog.java',
  'EndpointPermissionCatalogAppService.java',
  'DataScopeConfigService.java',
  'AdminUserService.java',
]);

// 角色比较/判断模式
const ROLE_DECISION_PATTERNS = [
  /RoleProfileCatalog\.[A-Z_]+_CODE\s*\.\s*equals\(/i,
  /RoleProfileCatalog\.[A-Z_]+_CODE\s*\.\s*equalsIgnoreCase\(/i,
  /\.getCode\(\)\s*\.\s*equals\(/i,
  /\.getRoleProfile\(\)\s*!=\s*null/i,
  /getRoleProfile\(\).*\.getCode\(\)/i,
  /user\.getRoleCode\(\).*equals/i,
  /roleCode\s*(!=|==)\s*null/i,
  /roleCode\s*\.\s*equals\(/i,
  /RoleProfile\.[A-Z_]+\s*==/i,
];

// 权限谓词方法名
const PERMISSION_METHOD_PATTERNS = [
  /\bcanSubmit\w*\s*\(/i,
  /\bcanApprove\w*\s*\(/i,
  /\bcanReject\w*\s*\(/i,
  /\bcanReview\w*\s*\(/i,
  /\bcanAccess\w*\s*\(/i,
  /\bcanManage\w*\s*\(/i,
  /\bhasRole\w*\s*\(/i,
  /\bisAdmin\w*\s*\(/i,
];

function getServiceFiles() {
  if (STAGED_ONLY) {
    const result = spawnSync('git', [
      'diff', '--cached', '--name-only', '--diff-filter=ACMR',
    ], { cwd: ROOT_DIR, encoding: 'utf8', env: gitEnv });
    return (result.stdout || '')
      .trim()
      .split(/\n/)
      .filter(f => f.startsWith('backend/src/main/java/') && /(Service|AppService)\.java$/.test(f));
  }

  const files = [];
  function walk(dir) {
    for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
      if (entry.name.startsWith('.') || SKIP_DIRS.has(entry.name)) continue;
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) walk(full);
      else if (/(Service|AppService)\.java$/.test(entry.name)) files.push(full);
    }
  }
  if (fs.existsSync(BACKEND_DIR)) walk(BACKEND_DIR);
  return files.map(f => path.relative(ROOT_DIR, f));
}

function isWhitelisted(relPath) {
  const basename = path.basename(relPath);
  return WHITELIST_FILES.has(basename);
}

function checkFile(relPath) {
  const fullPath = path.join(ROOT_DIR, relPath);
  if (!fs.existsSync(fullPath) || isWhitelisted(relPath)) return [];

  const content = fs.readFileSync(fullPath, 'utf8');
  const findings = [];

  // Rule 1: 直接使用 RoleProfileCatalog/RoleProfile 做角色判定，且未委托给 Policy
  const hasRoleProfileRef = /\bRoleProfile(Catalog)?\b/.test(content);
  const hasRoleDecision = ROLE_DECISION_PATTERNS.some(p => p.test(content));
  const callsPolicy = /\b[A-Z][a-zA-Z0-9_]*Policy\.[a-z][a-zA-Z0-9_]*\s*\(/.test(content);
  if (hasRoleProfileRef && hasRoleDecision && !callsPolicy) {
    findings.push('Service 中直接引用 RoleProfile/RoleProfileCatalog 做角色判定且未委托 Policy；' +
      '请将授权决策下沉到 core/*Policy 类。');
  }

  // Rule 2: 抛出 FORBIDDEN 但没有调用 *Policy.canXxx
  const hasForbidden = /HttpStatus\.FORBIDDEN\b/.test(content) ||
    /new\s+ResponseStatusException\s*\(\s*HttpStatus\.FORBIDDEN/.test(content);
  if (hasForbidden && !callsPolicy) {
    findings.push('Service 中直接抛出 FORBIDDEN (403)，但未调用任何 core *Policy 授权方法；' +
      '请通过 Policy.decide() → 映射 HTTP 状态码。');
  }

  return findings;
}

function main() {
  const files = getServiceFiles();
  if (files.length === 0) {
    console.log('No Service/AppService files to check.');
    process.exit(0);
  }

  let totalFindings = 0;
  for (const relPath of files) {
    const findings = checkFile(relPath);
    if (findings.length > 0) {
      totalFindings += findings.length;
      console.warn(`[core-policy-cr] ${relPath}`);
      for (const f of findings) {
        console.warn(`  ⚠ ${f}`);
      }
    }
  }

  if (totalFindings > 0) {
    console.warn('');
    console.warn(`检测到 ${totalFindings} 处 Service/AppService 层权限逻辑未下沉到 core Policy。`);
    console.warn('CR 强制项：权限/授权逻辑必须位于 core/*Policy，Service 只做查询→调用Policy→映射HTTP状态码。');
    process.exit(0);
  }

  console.log('Core policy CR check passed.');
  process.exit(0);
}

main();
