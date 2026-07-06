const HEADER_FIELD_MAP = {
    "证书名称": "name",
    "等级": "level",
    "认证机构": "issuer",
    "证书编号": "certificateNo",
    "发证日期": "issueDate",
    "证书有效期": "expiryDate",
    "代理机构": "agency",
    "代理机构联系人": "agencyContact",
    "认证范围": "certScope",
    "证书审核提醒": "certReviewNote",
    "附件文件名": "attachmentFileName"
  };

/**
 * qualification-import.ts
 *
 * E2E 辅助：资质批量导入 Excel 生成器（§4.1.3.4）
 *
 * 注意：xlsx (SheetJS) 库默认输出 `t="str"` inline formula string cell type，
 * Apache POI 对 `t="str"` cell 的 inline value 解析有兼容问题（getStringCellValue
 * 返回空），导致后端解析 0 行。本辅助改用 Python openpyxl 生成 xlsx：openpyxl
 * 输出标准 `t="s"` shared string cell type，POI 完全兼容。
 *
 * 11 列与后端 ImportQualificationAppService.parse 保持一致：
 *   证书名称 | 等级 | 认证机构 | 证书编号 | 发证日期 | 证书有效期 |
 *   代理机构 | 代理机构联系人 | 认证范围 | 证书审核提醒 | 附件文件名
 *
 * 必填 9 项：name/issuer/certificateNo/issueDate/expiryDate/agency/agencyContact/certScope/attachmentFileName
 * 代理机构联系人：纯文本必填（CO-525）
 * 附件命名：QUAL_{证书编号}_{序号}_{文件名}.{ext}
 */
import { spawnSync } from 'node:child_process'
import * as path from 'node:path'
import * as fs from 'node:fs'

export const QUALIFICATION_IMPORT_HEADERS = [
  '证书名称', '等级', '认证机构', '证书编号', '发证日期', '证书有效期',
  '代理机构', '代理机构联系人', '认证范围', '证书审核提醒', '附件文件名'
]

/**
 * 生成一行合规的资质导入数据。
 * @param override - 覆盖默认值
 */
export function buildValidQualificationRow(override = {}) {
  const certNo = override.certificateNo || `E2E-IMP-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`
  return {
    name: 'E2E 导入测试证书',
    level: 'FIRST',
    issuer: '中国计量认证中心',
    certificateNo: certNo,
    issueDate: '2024-01-15',
    expiryDate: '2027-12-31',
    agency: 'E2E 代理认证公司',
    agencyContact: '13800138000',
    certScope: 'E2E 测试覆盖范围：产品设计/生产/销售',
    certReviewNote: '每年 3 月年审',
    attachmentFileName: `QUAL_${certNo}_01_示例证书.pdf`,
    ...override
  }
}

function rowsToJsonPayload(rows) {
  return JSON.stringify({ headers: QUALIFICATION_IMPORT_HEADERS, rows })
}

/**
 * 通过 Python openpyxl 生成 xlsx buffer（POI 兼容）。
 * @param rows - 二维数组，第一行是表头
 */
function generateXlsxViaOpenpyxl(rows) {
  const scriptPath = path.join(import.meta.dirname, 'generate_xlsx.py')
  if (!fs.existsSync(scriptPath)) {
    throw new Error(`openpyxl 生成脚本缺失：${scriptPath}`)
  }
  const payload = JSON.stringify({ rows })
  const result = spawnSync('python3', [scriptPath], {
    input: payload,
    maxBuffer: 50 * 1024 * 1024
  })
  if (result.status !== 0) {
    throw new Error(`openpyxl 生成失败：${result.stderr?.toString()}`)
  }
  return Buffer.from(result.stdout)
}

/**
 * 合法导入 Excel：默认 2 条合规行（不同证书编号）。
 */
export function generateValidQualificationImportExcel(rows = null) {
  const list = rows || [
    buildValidQualificationRow(),
    buildValidQualificationRow({ name: 'E2E 导入证书-2', certificateNo: `E2E-IMP2-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}` })
  ]
  return generateXlsxViaOpenpyxl([
    QUALIFICATION_IMPORT_HEADERS,
    ...list.map((r) => QUALIFICATION_IMPORT_HEADERS.map((h) => { const f = HEADER_FIELD_MAP[h]; return f && f in r ? r[f] : null }))
  ])
}

/**
 * 非法导入 Excel：1 条合法 + 多类非法行（缺必填/联系方式错/日期顺序错/附件命名错）
 */
export function generateInvalidQualificationImportExcel() {
  const base = buildValidQualificationRow()
  const invalid = [
    base,
    buildValidQualificationRow({ name: '', certificateNo: `E2E-MISS-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}` }),
    buildValidQualificationRow({ name: 'E2E 联系人空', certificateNo: `E2E-CONT-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`, agencyContact: '' }),
    buildValidQualificationRow({ name: 'E2E 日期顺序错', certificateNo: `E2E-DATE-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`, issueDate: '2027-12-31', expiryDate: '2024-01-15' }),
    buildValidQualificationRow({ name: 'E2E 附件命名错', certificateNo: `E2E-FILE-${Date.now()}-${Math.random().toString(36).slice(2, 6).toUpperCase()}`, attachmentFileName: 'wrong_filename.pdf' })
  ]
  return generateXlsxViaOpenpyxl([
    QUALIFICATION_IMPORT_HEADERS,
    ...invalid.map((r) => QUALIFICATION_IMPORT_HEADERS.map((h) => { const f = HEADER_FIELD_MAP[h]; return f && f in r ? r[f] : null }))
  ])
}

// 静默使用避免 lint 报警
void rowsToJsonPayload
