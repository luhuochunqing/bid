// Input: wiki pages, source catalog, and pending ingest changes
// Output: normalized wiki pages, implementation playbook pages, and refreshed page catalog
// Pos: scripts/ - Wiki synthesis and indexing pipeline for engineering + implementation spaces
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import {
  buildFrontmatter,
  catalogRoot,
  ensureWikiDirs,
  isoDate,
  pagesRoot,
  parseFrontmatter,
  readJsonOrDefault,
  relToRepo,
  resolvePagePathFromLink,
  slugFromPagePath,
  walkFiles,
  writeJson,
} from './wiki-common.mjs'

const pageCatalogJson = path.join(catalogRoot, 'page-catalog.json')
const pendingJson = path.join(catalogRoot, 'pending-build.json')

function ensureImplementationPages() {
  const implementationDir = path.join(pagesRoot, 'implementation')
  fs.mkdirSync(implementationDir, { recursive: true })

  const templates = [
    {
      file: 'delivery-playbook.md',
      title: '实施交付作战包总览',
      body: `# 实施交付作战包总览\n\n本页定义实施顾问/项目经理在项目周期内的核心工作包，并与研发知识页面形成双向追溯。\n\n## 核心工作包\n\n- [[implementation/milestones]]\n- [[implementation/risk-register]]\n- [[implementation/weekly-status]]\n- [[implementation/acceptance-and-closure]]\n\n## 研发追溯入口\n\n- [[architecture]]\n- [[modules]]\n- [[deployment]]\n`,
      sources: [
        'docs/西域数智化投标管理平台-实施计划书-7月10日上线版.md',
        'docs/UAT_PLAN.md',
        'docs/GO_LIVE_CHECKLIST.md',
      ],
    },
    {
      file: 'milestones.md',
      title: '实施里程碑与依赖',
      body: `# 实施里程碑与依赖\n\n## 里程碑清单\n\n1. 项目启动与调研\n2. 蓝图设计与方案确认\n3. 系统实现与配置一期\n4. 集成联调与数据初始化\n5. UAT、培训与上线准备\n6. 生产上线与试运行\n\n## 依赖追溯\n\n- 技术依赖：[[deployment]]、[[architecture]]\n- 业务依赖：[[requirements]]、[[business-process]]\n`,
      sources: [
        'docs/西域数智化投标管理平台-实施计划书-7月10日上线版.md',
        'docs/DELIVERY_BLOCKERS_SCHEDULE.md',
      ],
    },
    {
      file: 'risk-register.md',
      title: '实施风险台账',
      body: `# 实施风险台账\n\n| 风险 | 等级 | 触发信号 | 应对策略 | 追溯页面 |\n|---|---|---|---|---|\n| 集成链路不稳定 | 高 | API 联调失败率升高 | 预演 + 回滚演练 + 降级开关 | [[deployment]] |\n| 范围漂移 | 高 | 非白名单需求插入 | 严格按正式范围核对 | [[requirements]] |\n| 验收证据不足 | 中 | UAT 记录缺失 | 固化报告模板 + 每周补齐 | [[implementation/acceptance-and-closure]] |\n\n## 关联\n\n- [[implementation/weekly-status]]\n- [[team-and-timeline]]\n`,
      sources: ['docs/DELIVERY_BLOCKERS_SCHEDULE.md', 'docs/UAT_PLAN.md'],
    },
    {
      file: 'weekly-status.md',
      title: '实施周报与例会纪要模板',
      body: `# 实施周报与例会纪要模板\n\n## 周报模板\n\n- 本周目标\n- 实际完成\n- 偏差与原因\n- 下周计划\n- 风险与阻塞\n\n## 例会纪要模板\n\n- 会议主题/时间/参与者\n- 关键决策\n- Action Items（负责人/截止日期）\n- 需研发支持事项（回链到工程页面）\n\n## 回链参考\n\n- [[implementation/risk-register]]\n- [[modules]]\n- [[deployment]]\n`,
      sources: ['docs/启动会项目组织架构与沟通矩阵.md', 'docs/tasks/production-task-breakdown.md'],
    },
    {
      file: 'acceptance-and-closure.md',
      title: '实施验收与问题闭环',
      body: `# 实施验收与问题闭环\n\n## 验收清单\n\n- 范围对齐：与商业范围白名单一致\n- 测试证据：UAT、SIT、回归报告完整\n- 上线演练：回滚与 smoke 结果可追溯\n- 培训交接：角色化培训记录齐全\n\n## 问题闭环\n\n- 问题描述\n- 影响范围\n- 根因\n- 处置方案\n- 验证结果\n- 沉淀到知识库页面\n\n## 回链\n\n- [[deployment]]\n- [[requirements]]\n- [[implementation/delivery-playbook]]\n`,
      sources: ['docs/UAT_PLAN.md', 'docs/UAT_SIGNOFF_TEMPLATE.md', 'docs/GO_LIVE_CHECKLIST.md'],
    },
  ]

  for (const template of templates) {
    const absPath = path.join(implementationDir, template.file)
    if (fs.existsSync(absPath)) {
      continue
    }

    const today = isoDate()
    const content = buildFrontmatter(
      {
        title: template.title,
        space: 'implementation',
        category: 'guide',
        tags: ['implementation', 'delivery'],
        sources: template.sources,
        backlinks: [],
        created: today,
        updated: today,
        health_checked: today,
      },
      `${template.body}\n`
    )
    fs.writeFileSync(absPath, content, 'utf8')
  }
}

function getPageTitle(frontmatter, body, slug) {
  if (frontmatter.title) {
    return frontmatter.title
  }
  const heading = body.match(/^#\s+(.+)$/m)
  if (heading) {
    return heading[1].trim()
  }
  return slug
}

function inferSpace(absPath, frontmatter) {
  if (frontmatter.space === 'engineering' || frontmatter.space === 'implementation') {
    return frontmatter.space
  }
  const rel = path.relative(pagesRoot, absPath).replace(/\\/g, '/')
  if (rel.startsWith('implementation/')) {
    return 'implementation'
  }
  return 'engineering'
}

function normalizeSources(frontmatter) {
  if (Array.isArray(frontmatter.sources) && frontmatter.sources.length > 0) {
    const existing = frontmatter.sources.filter((sourcePath) =>
      fs.existsSync(path.join(process.cwd(), sourcePath))
    )
    if (existing.length > 0) {
      return existing
    }
  }
  return ['README.md']
}

function collectLinks(body) {
  return Array.from(body.matchAll(/\[\[([^\]]+)\]\]/g)).map((m) => m[1].trim())
}

function resolveLinkTargetSlug(rawTarget, knownSlugs) {
  const cleaned = rawTarget.replace(/\.md$/, '').replace(/^\/+/, '').trim()
  if (knownSlugs.has(cleaned)) {
    return cleaned
  }

  const byName = Array.from(knownSlugs).find((slug) => slug.endsWith(`/${cleaned}`) || slug === cleaned)
  if (byName) {
    return byName
  }

  const absPath = resolvePagePathFromLink(cleaned)
  if (absPath) {
    return slugFromPagePath(absPath)
  }

  return null
}

function upsertWikiIndex(records) {
  const lines = [
    '# Page Catalog / Wiki 页面索引',
    '',
    `> Updated: ${isoDate()} · Generated by \`npm run wiki:build\``,
    '',
  ]

  const spaces = ['engineering', 'implementation']

  for (const space of spaces) {
    const bySpace = records.filter((record) => record.space === space)
    if (bySpace.length === 0) continue

    lines.push(`## ${space === 'engineering' ? 'Engineering Space' : 'Implementation Space'}`)
    lines.push('')

    const categories = Array.from(new Set(bySpace.map((record) => record.category || 'guide'))).sort((a, b) => a.localeCompare(b))

    for (const category of categories) {
      const list = bySpace.filter((record) => (record.category || 'guide') === category).sort((a, b) => a.slug.localeCompare(b.slug))
      lines.push(`### ${category}`)
      lines.push('')
      lines.push('| slug | title | sources | updated | backlinks |')
      lines.push('|---|---|---:|---|---:|')
      for (const record of list) {
        lines.push(
          `| [${record.slug}](pages/${record.slug}.md) | ${record.title} | ${record.sources.length} | ${record.updated} | ${record.backlinks.length} |`
        )
      }
      lines.push('')
    }
  }

  lines.push('## Summary')
  lines.push('')
  lines.push(`- pages: ${records.length}`)
  lines.push(`- engineering: ${records.filter((r) => r.space === 'engineering').length}`)
  lines.push(`- implementation: ${records.filter((r) => r.space === 'implementation').length}`)
  lines.push('')

  fs.writeFileSync(path.join(path.dirname(pagesRoot), 'PAGE_INDEX.md'), `${lines.join('\n')}\n`, 'utf8')
}

function rebuildWikiHome(records) {
  const engineering = records.filter((record) => record.space === 'engineering').sort((a, b) => a.slug.localeCompare(b.slug))
  const implementation = records.filter((record) => record.space === 'implementation').sort((a, b) => a.slug.localeCompare(b.slug))
  const today = isoDate()

  const body = [
    '# 西域投标管理平台知识库',
    '',
    '> 本知识库按研发与实施双空间组织，支持原始 Office 文件混合摄入和增量编译。',
    '',
    '## 快速入口',
    '',
    '- 规则协议：`WIKI.md`',
    '- 源文档编目：`.wiki/INDEX.md`',
    '- 页面索引：`.wiki/PAGE_INDEX.md`',
    '- 抽取中间层：`.wiki/extracts/`',
    '- 产物回流层：`.wiki/outputs/`',
    '',
    '## Engineering Space',
    '',
    ...engineering.map((record) => `- [[${record.slug}]] — ${record.title}`),
    '',
    '## Implementation Space',
    '',
    ...implementation.map((record) => `- [[${record.slug}]] — ${record.title}`),
    '',
    '## 操作命令',
    '',
    '1. `npm run wiki:ingest`',
    '2. `npm run wiki:build`',
    '3. `npm run wiki:check`',
    '',
  ].join('\n')

  const content = buildFrontmatter(
    {
      title: '西域投标管理平台知识库',
      space: 'engineering',
      category: 'guide',
      tags: ['首页', '导航', 'wiki'],
      sources: ['WIKI.md', '.wiki/INDEX.md', '.wiki/PAGE_INDEX.md'],
      backlinks: [],
      created: today,
      updated: today,
      health_checked: today,
    },
    `${body}\n`
  )

  fs.writeFileSync(path.join(pagesRoot, '_index.md'), content, 'utf8')
}

function main() {
  ensureWikiDirs()
  ensureImplementationPages()

  const pending = readJsonOrDefault(pendingJson, { pending_paths: [] })
  const pendingPaths = new Set(pending.pending_paths || [])

  const pageAbsPaths = walkFiles(pagesRoot)
    .filter((absPath) => absPath.endsWith('.md'))
    .sort((a, b) => a.localeCompare(b))

  const pageDrafts = []

  for (const pageAbsPath of pageAbsPaths) {
    const source = fs.readFileSync(pageAbsPath, 'utf8')
    const { frontmatter, body } = parseFrontmatter(source)
    const slug = slugFromPagePath(pageAbsPath)

    const title = getPageTitle(frontmatter, body, slug)
    const space = inferSpace(pageAbsPath, frontmatter)
    const category = frontmatter.category || 'guide'
    const tags = Array.isArray(frontmatter.tags) ? frontmatter.tags : []
    const sources = normalizeSources(frontmatter)
    const created = frontmatter.created || isoDate()
    const hasPendingSource = sources.some((sourcePath) => pendingPaths.has(sourcePath))
    const updated = hasPendingSource ? isoDate() : frontmatter.updated || isoDate()

    pageDrafts.push({
      pageAbsPath,
      slug,
      title,
      space,
      category,
      tags,
      sources,
      created,
      updated,
      body,
      links: collectLinks(body),
    })
  }

  const slugSet = new Set(pageDrafts.map((draft) => draft.slug))
  const backlinks = new Map()

  for (const draft of pageDrafts) {
    for (const link of draft.links) {
      const target = resolveLinkTargetSlug(link, slugSet)
      if (!target) {
        continue
      }
      const list = backlinks.get(target) || []
      if (!list.includes(draft.slug)) {
        list.push(draft.slug)
      }
      backlinks.set(target, list)
    }
  }

  const today = isoDate()
  const records = []

  for (const draft of pageDrafts) {
    const pageBacklinks = (backlinks.get(draft.slug) || []).sort((a, b) => a.localeCompare(b))

    const content = buildFrontmatter(
      {
        title: draft.title,
        space: draft.space,
        category: draft.category,
        tags: draft.tags,
        sources: draft.sources,
        backlinks: pageBacklinks,
        created: draft.created,
        updated: draft.updated,
        health_checked: today,
      },
      draft.body
    )

    fs.writeFileSync(draft.pageAbsPath, content, 'utf8')

    records.push({
      slug: draft.slug,
      title: draft.title,
      space: draft.space,
      category: draft.category,
      sources: draft.sources,
      backlinks: pageBacklinks,
      updated: draft.updated,
      health_checked: today,
      path: relToRepo(draft.pageAbsPath),
    })
  }

  records.sort((a, b) => a.slug.localeCompare(b.slug))

  rebuildWikiHome(records)
  upsertWikiIndex(records)
  writeJson(pageCatalogJson, records)

  console.log(`wiki:build completed. pages=${records.length}`)
}

main()
