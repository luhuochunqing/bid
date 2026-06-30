import { describe, it, expect } from 'vitest'
import { formatOperationType, formatChangeSummary } from './personnelConstants.js'

describe('formatOperationType', () => {
  it('映射 CREATE 为新建', () => {
    expect(formatOperationType('CREATE')).toBe('新建')
  })

  it('映射 UPDATE 为编辑', () => {
    expect(formatOperationType('UPDATE')).toBe('编辑')
  })

  it('映射 DELETE 为删除', () => {
    expect(formatOperationType('DELETE')).toBe('删除')
  })

  it('映射 RESTORE 为恢复', () => {
    expect(formatOperationType('RESTORE')).toBe('恢复')
  })

  it('映射 CERTIFICATE_ADD 为新增证书', () => {
    expect(formatOperationType('CERTIFICATE_ADD')).toBe('新增证书')
  })

  it('映射 CERTIFICATE_REMOVE 为删除证书', () => {
    expect(formatOperationType('CERTIFICATE_REMOVE')).toBe('删除证书')
  })

  it('映射 CERTIFICATE_UPDATE 为修改证书', () => {
    expect(formatOperationType('CERTIFICATE_UPDATE')).toBe('修改证书')
  })

  it('映射 EDUCATION_ADD 为新增教育经历', () => {
    expect(formatOperationType('EDUCATION_ADD')).toBe('新增教育经历')
  })

  it('映射 EDUCATION_REMOVE 为删除教育经历', () => {
    expect(formatOperationType('EDUCATION_REMOVE')).toBe('删除教育经历')
  })

  it('映射 EDUCATION_UPDATE 为修改教育经历', () => {
    expect(formatOperationType('EDUCATION_UPDATE')).toBe('修改教育经历')
  })

  it('映射 BATCH_IMPORT_PERSONNEL 为批量导入人员', () => {
    expect(formatOperationType('BATCH_IMPORT_PERSONNEL')).toBe('批量导入人员')
  })

  it('映射 BATCH_EXPORT_PERSONNEL 为批量导出人员', () => {
    expect(formatOperationType('BATCH_EXPORT_PERSONNEL')).toBe('批量导出人员')
  })

  it('映射 ATTACHMENT_REPLACE 为替换附件', () => {
    expect(formatOperationType('ATTACHMENT_REPLACE')).toBe('替换附件')
  })

  it('未知类型原样返回', () => {
    expect(formatOperationType('UNKNOWN_TYPE')).toBe('UNKNOWN_TYPE')
  })

  it('空值返回 -', () => {
    expect(formatOperationType(null)).toBe('-')
    expect(formatOperationType('')).toBe('-')
    expect(formatOperationType(undefined)).toBe('-')
  })
})

describe('formatChangeSummary', () => {
  it('UPDATE 类型渲染为 field: oldValue → newValue', () => {
    const details = [{ field: 'name', oldValue: '张三', newValue: '李四' }]
    expect(formatChangeSummary('UPDATE', details)).toBe('name: 张三 → 李四')
  })

  it('DELETE 类型渲染为 field: oldValue → newValue', () => {
    const details = [{ field: 'status', oldValue: 'ACTIVE', newValue: 'INACTIVE' }]
    expect(formatChangeSummary('DELETE', details)).toBe('status: ACTIVE → INACTIVE')
  })

  it('CERTIFICATE_ADD 类型渲染为 field: newValue（无 oldValue）', () => {
    const details = [{ field: 'certificate', oldValue: '', newValue: 'PMP' }]
    expect(formatChangeSummary('CERTIFICATE_ADD', details)).toBe('certificate: PMP')
  })

  it('CERTIFICATE_REMOVE 类型渲染为 field: oldValue（无 newValue）', () => {
    const details = [{ field: 'certificate', oldValue: 'PMP', newValue: '' }]
    expect(formatChangeSummary('CERTIFICATE_REMOVE', details)).toBe('certificate: PMP')
  })

  it('CERTIFICATE_UPDATE 类型渲染为 field: oldValue → newValue', () => {
    const details = [{ field: 'certificateNumber', oldValue: 'C001', newValue: 'C002' }]
    expect(formatChangeSummary('CERTIFICATE_UPDATE', details)).toBe('certificateNumber: C001 → C002')
  })

  it('EDUCATION_ADD 类型渲染为 field: newValue', () => {
    const details = [{ field: 'education', oldValue: '', newValue: '北大' }]
    expect(formatChangeSummary('EDUCATION_ADD', details)).toBe('education: 北大')
  })

  it('EDUCATION_REMOVE 类型渲染为 field: oldValue', () => {
    const details = [{ field: 'education', oldValue: '北大', newValue: '' }]
    expect(formatChangeSummary('EDUCATION_REMOVE', details)).toBe('education: 北大')
  })

  it('BATCH_IMPORT_PERSONNEL 类型渲染为 field: newValue', () => {
    const details = [{ field: 'count', oldValue: '', newValue: '50' }]
    expect(formatChangeSummary('BATCH_IMPORT_PERSONNEL', details)).toBe('count: 50')
  })

  it('ATTACHMENT_REPLACE 类型渲染为 field: oldValue → newValue', () => {
    const details = [{ field: 'attachment', oldValue: 'old.pdf', newValue: 'new.pdf' }]
    expect(formatChangeSummary('ATTACHMENT_REPLACE', details)).toBe('attachment: old.pdf → new.pdf')
  })

  it('多条变更用分号连接', () => {
    const details = [
      { field: 'name', oldValue: '张三', newValue: '李四' },
      { field: 'departmentName', oldValue: '技术部', newValue: '市场部' }
    ]
    expect(formatChangeSummary('UPDATE', details)).toBe('name: 张三 → 李四; departmentName: 技术部 → 市场部')
  })

  it('空变更列表返回空字符串', () => {
    expect(formatChangeSummary('UPDATE', [])).toBe('')
  })

  it('null 变更列表返回空字符串', () => {
    expect(formatChangeSummary('UPDATE', null)).toBe('')
  })
})
