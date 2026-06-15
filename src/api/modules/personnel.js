// Input: httpClient and feature-availability helpers
// Output: personnelApi - personnel CRUD accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const CERT_TYPE_MAP = {
  CONSTRUCTOR: '建造师',
  PMP: 'PMP',
  ENGINEER: '工程师',
  ACCOUNTANT: '会计师',
  LAWYER: '律师',
  SECURITY: '安全工程师',
  IT: 'IT类证书',
  OTHER: '其他'
}

const STATUS_MAP = {
  ACTIVE: '在职',
  INACTIVE: '停用',
  TERMINATED: '离职'
}

function formatDate(d) {
  return d ? String(d).slice(0, 10) : null
}

function normalizePersonnel(p) {
  return {
    id: p.id,
    name: p.name || '-',
    employeeNumber: p.employeeNumber || '-',
    departmentCode: p.departmentCode || '',
    departmentName: p.departmentName || '-',
    gender: p.gender || '',
    entryDate: formatDate(p.entryDate),
    birthDate: formatDate(p.birthDate),
    phone: p.phone || '',
    education: p.education || '-',
    technicalTitle: p.technicalTitle || '-',
    remark: p.remark || '',
    status: p.status || 'ACTIVE',
    statusLabel: STATUS_MAP[p.status] || p.status || '未知',
    attachmentUrl: p.attachmentUrl || '',
    certificates: Array.isArray(p.certificates) ? p.certificates.map(normalizeCert) : [],
    educations: Array.isArray(p.educations) ? p.educations.map(normalizeEducation) : [], // 新增支持
    totalProjects: p.totalProjects || 0,
    // 4.3 查看证书 h5 计算字段
    yearsOfService: p.yearsOfService ?? null,
    highestEducation: p.highestEducation || p.education || '-',
    certificateCount: p.certificateCount ?? (Array.isArray(p.certificates) ? p.certificates.length : 0),
    expiringCertificatesCount: p.expiringCertificatesCount ?? 0,
    createdAt: formatDate(p.createdAt),
    updatedAt: formatDate(p.updatedAt)
  }
}

function normalizeEducation(e) {
  return {
    id: e.id || null,
    schoolName: e.schoolName || '-',
    startDate: formatDate(e.startDate),
    endDate: formatDate(e.endDate),
    highestEducation: e.highestEducation || '-',
    studyForm: e.studyForm || '-',
    major: e.major || ''
  }
}

function normalizeCert(c) {
  const remainingDays = c.expiryDate
    ? Math.ceil((new Date(c.expiryDate) - Date.now()) / 86400000)
    : null
  // 4.3.1.3 优先使用后端计算的 status，否则前端兜底
  const status = c.status || (c.isPermanent ? 'PERMANENT' : c.expired ? 'EXPIRED' : remainingDays !== null && remainingDays <= 30 ? 'EXPIRING' : 'VALID')
  return {
    id: c.id,
    name: c.name || '-',
    certificateNumber: c.certificateNumber || '-',
    type: c.type || 'OTHER',
    typeLabel: CERT_TYPE_MAP[c.type] || c.type || '其他',
    issueDate: formatDate(c.issueDate),
    expiryDate: formatDate(c.expiryDate),
    attachmentUrl: c.attachmentUrl || '',
    isPermanent: c.isPermanent || false,
    expired: c.expired || remainingDays === null ? false : remainingDays < 0,
    remainingDays: remainingDays ?? null,
    status
  }
}

function buildPayload(data) {
  return {
    name: data.name || '',
    employeeNumber: data.employeeNumber || '',
    departmentCode: data.departmentCode || '',
    departmentName: data.departmentName || '',
    // 4.3 查看证书 h5 新增视图字段
    gender: data.gender || '',
    entryDate: data.entryDate || null,
    birthDate: data.birthDate || null,
    phone: data.phone || '',
    education: data.education || '',
    technicalTitle: data.technicalTitle || '',
    remark: data.remark || '',
    attachmentUrl: data.attachmentUrl || '',
    certificates: Array.isArray(data.certificates)
      ? data.certificates.map(c => ({
          name: c.name || '',
          certificateNumber: c.certificateNumber || '',
          type: c.type || 'OTHER',
          issueDate: c.issueDate || null,
          expiryDate: c.expiryDate || null,
          attachmentUrl: c.attachmentUrl || ''
        }))
      : [],
    // 新增：教育经历（支持蓝图 4.3 新增证书 Tab 2）
    educations: Array.isArray(data.educations)
      ? data.educations.map(e => {
          // 规范化 month picker "YYYY-MM" 为 "YYYY-MM-01" 以便后端 LocalDate 反序列化成功（DB DATE not null）
          const normDate = (d) => {
            if (!d) return null
            const s = String(d)
            if (/^\d{4}-\d{2}$/.test(s)) return s + '-01'
            return s
          }
          return {
            schoolName: e.schoolName || '',
            startDate: normDate(e.startDate),
            endDate: normDate(e.endDate),
            highestEducation: e.highestEducation || '',
            studyForm: e.studyForm || '',
            major: e.major || ''
          }
        })
      : []
  }
}

export const personnelApi = {
  async getList(params = {}) {
    const qs = new URLSearchParams()
    // 基础 + 遗留
    if (params.keyword) qs.set('keyword', params.keyword)
    if (params.departmentCode) qs.set('departmentCode', params.departmentCode)
    if (params.status) qs.set('status', params.status)
    if (params.certificateType) qs.set('certificateType', params.certificateType)

    // === 筛选与搜索 h5 全量新参数 ===
    if (params.gender) qs.set('gender', params.gender)
    if (params.majorKeyword) qs.set('majorKeyword', params.majorKeyword)
    if (params.certificateKeyword) qs.set('certificateKeyword', params.certificateKeyword)
    if (params.entryDateFrom) qs.set('entryDateFrom', params.entryDateFrom)
    if (params.entryDateTo) qs.set('entryDateTo', params.entryDateTo)

    // 多选数组（Spring @RequestParam List<String> 支持重复 key）
    const appendList = (key, arr) => {
      if (Array.isArray(arr)) {
        arr.forEach(v => { if (v) qs.append(key, v) })
      }
    }
    appendList('highestEducations', params.highestEducations)
    appendList('studyForms', params.studyForms)
    appendList('certificateStatuses', params.certificateStatuses)

    const query = qs.toString() ? `?${qs.toString()}` : ''
    const res = await httpClient.get(`/api/knowledge/personnel${query}`)
    return { ...res, data: Array.isArray(res?.data) ? res.data.map(normalizePersonnel) : [] }
  },

  async getDetail(id) {
    const res = await httpClient.get(`/api/knowledge/personnel/${id}`)
    return { ...res, data: normalizePersonnel(res?.data) }
  },

  async create(data) {
    const res = await httpClient.post('/api/knowledge/personnel', buildPayload(data))
    return { ...res, data: normalizePersonnel(res?.data) }
  },

  async update(id, data) {
    const res = await httpClient.put(`/api/knowledge/personnel/${id}`, buildPayload(data))
    const raw = res?.data || {}

    // 新响应结构：{ personnel, warnings }
    if (raw.personnel) {
      return {
        ...res,
        data: {
          personnel: normalizePersonnel(raw.personnel),
          warnings: Array.isArray(raw.warnings) ? raw.warnings : []
        }
      }
    }

    // 兼容旧结构（以防万一）
    return { ...res, data: normalizePersonnel(raw) }
  },

  async delete(id, reason) {
    return httpClient.delete(`/api/knowledge/personnel/${id}`, { reason })
  },

  async restore(id) {
    return httpClient.post(`/api/knowledge/personnel/${id}/restore`)
  },

  /**
   * 上传人员证书附件（对应 4.3 "新增证书" Tab3 证书附件）。
   * multipart 上传，返回 {data: url}
   */
  async uploadCertAttachment(personnelId, certId, file) {
    const fd = new FormData()
    fd.append('file', file)
    return httpClient.post(
      `/api/knowledge/personnel/${personnelId}/certificates/${certId}/attachment`,
      fd,
      { headers: { 'Content-Type': 'multipart/form-data' } }
    )
  },

  /**
   * 查询人员操作日志（4.3.1.3 详情抽屉 Tab 4）。
   */
  async getOperationLogs(personnelId) {
    const res = await httpClient.get(`/api/knowledge/personnel/${personnelId}/operation-logs`)
    return { ...res, data: Array.isArray(res?.data) ? res.data : [] }
  }
}

export default personnelApi
