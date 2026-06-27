#!/usr/bin/env node
// Input: dist/assets/*.js（前端构建产物）
// Output: 失败（exit 1）如果产物含 dev API 地址（localhost/127.0.0.1:port）
// Pos: scripts/ — 部署构建门禁，防止 dev baseURL 的前端被误部署到服务器
// 维护声明: 与 src/api/config.js 的 normalizeApiBaseUrl + .env.api / scripts/release/package-release.sh 的 fallback 同步。
//
// 背景：前端 axios baseURL 完全由构建时 VITE_API_BASE_URL 决定。若构建用了 dev 地址
// （localhost:18086 / 127.0.0.1:18089），部署到任何服务器后前端都会去调本地 dev 后端 → 全 API 失败。
// 历史上曾因此导致 IP:8080 部署的前端调错后端 + 跨域 403，排查链路极长。本 check 在构建期就拦住。

import { readdirSync, readFileSync, existsSync } from 'fs'
import { join } from 'path'

const DIST_ASSETS = join(process.cwd(), 'dist', 'assets')
// 拦截不该出现在部署产物里的 API base：dev 地址（localhost/127.0.0.1）+ 内网 IP（带端口）。
// 这些作 baseURL 几乎必然跨域（前端访问 origin ≠ API base），应改为同源（baseURL 空，npm run build:samesite）。
// 域名 base（如 https://winbid-test.ehsy.com，无端口）不拦——从该域名访问是同域合法的。
// 仍排除 `window.location.href||"http://localhost"`（无端口，非 API baseURL）这类合法引用。
const BAD_API_BASE = /https?:\/\/(?:localhost|127\.0\.0\.1|10\.\d{1,3}\.\d{1,3}\.\d{1,3}|172\.(?:1[6-9]|2\d|3[01])\.\d{1,3}\.\d{1,3}|192\.168\.\d{1,3}\.\d{1,3}):\d+/g

if (!existsSync(DIST_ASSETS)) {
  console.error(`❌ check:frontend-api-base: 找不到 ${DIST_ASSETS}，请先构建（npm run build:api / build:samesite）`)
  process.exit(1)
}

const hits = []
for (const f of readdirSync(DIST_ASSETS)) {
  if (!f.endsWith('.js')) continue
  const content = readFileSync(join(DIST_ASSETS, f), 'utf8')
  const found = [...content.matchAll(BAD_API_BASE)].map(m => m[0])
  if (found.length) hits.push({ file: f, urls: [...new Set(found)] })
}

if (hits.length) {
  console.error('❌ 前端构建产物含 dev/内网 IP API 地址（localhost/127.0.0.1/内网IP:port），不能部署：')
  for (const h of hits) console.error(`   ${h.file}: ${h.urls.join(', ')}`)
  console.error('')
  console.error('   部署构建必须用生产 baseURL：')
  console.error('     · 内网 / Spring Boot 一体（同源）：npm run build:samesite')
  console.error('     · 公网 / 域名：            PRODUCTION_API_BASE_URL=https://<域名> ./scripts/release/package-release.sh')
  console.error('     · 内网 release（同源）：    VITE_API_BASE_URL= ./scripts/release/package-release.sh')
  console.error('   dev 地址只在本地 dev 有效，部署到服务器会让前端调错后端。')
  process.exit(1)
}

console.log('✅ 前端产物不含 dev API 地址（baseURL 为同源或生产域名，可部署）')
