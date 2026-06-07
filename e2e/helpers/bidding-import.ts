/**
 * bidding-import.ts
 *
 * E2E 辅助：标讯批量导入 Excel 生成器
 * 用于 A1-4+ 填充被 skip 的“批量导入完整流程”测试。
 *
 * 依赖：xlsx (已作为 devDependency 添加)
 * 表头与后端 TenderImportService.HEADERS 保持一致。
 */

import * as XLSX from 'xlsx';

const HEADERS = [
  '标讯标题*', '招标机构*', '采购单位*', '总部所在地*',
  '报名截止时间*', '开标时间*', '联系人*', '联系方式*',
  '客户类型*', '优先级*',
  '预算（元）', '行业', '描述', '标签（多个用逗号分隔）'
];

/**
 * 生成合法的标讯导入 Excel（至少 1 条完整数据）
 * 可扩展支持多行 + CRM 自动分配场景。
 */
export function generateValidBiddingImportExcel(rows: Array<Record<string, any>> = []): Buffer {
  const wb = XLSX.utils.book_new();
  const wsData = [HEADERS];

  if (rows.length === 0) {
    // 默认一条合法示例（可根据实际字典调整）
    wsData.push([
      '测试标讯-合法-' + Date.now(),
      '测试招标代理公司',
      '测试采购单位',
      '北京',
      '2026-12-31 17:00:00',
      '2026-12-25 09:30:00',
      '测试联系人',
      '13800138000',
      '央企集团',
      'A',
      '1500000',
      '数据中心',
      'E2E 合法测试数据',
      '测试标签'
    ]);
  } else {
    rows.forEach(row => {
      const line = HEADERS.map(h => row[h] ?? row[h.replace('*', '')] ?? '');
      wsData.push(line);
    });
  }

  const ws = XLSX.utils.aoa_to_sheet(wsData);
  XLSX.utils.book_append_sheet(wb, ws, '标讯导入');

  // 可选：添加字典参考 sheet（与后端模板一致）
  const dictWs = XLSX.utils.aoa_to_sheet([
    ['地区（总部所在地）', '客户类型', '优先级'],
    ...Array.from({ length: 5 }, (_, i) => ['北京', '央企集团', 'A'])
  ]);
  XLSX.utils.book_append_sheet(wb, dictWs, '字典参考');

  const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
  return buf;
}

/**
 * 生成包含错误的标讯导入 Excel（用于测试行级错误提示）
 * 重点支持：必填缺失、格式错误 等场景，触发后端返回“第 X 行”错误。
 */
export function generateInvalidBiddingImportExcel(options: {
  missingRequired?: boolean;
  badDateFormat?: boolean;
  duplicateInFile?: boolean;
} = {}): Buffer {
  const wb = XLSX.utils.book_new();
  const wsData = [HEADERS];

  // 第 2 行：合法
  wsData.push([
    '测试标讯-合法行',
    '测试招标代理',
    '测试采购单位',
    '北京',
    '2026-12-31 17:00:00',
    '2026-12-25 09:30:00',
    '联系人',
    '13800138000',
    '央企集团',
    'A',
    '1000000',
    'IT',
    '描述',
    '标签'
  ]);

  if (options.missingRequired) {
    // 第 3 行：缺少必填（标讯标题为空）
    wsData.push([
      '', // 标题缺失
      '测试招标代理2',
      '测试采购单位2',
      '上海',
      '2026-12-31 17:00:00',
      '2026-12-25 09:30:00',
      '联系人2',
      '13900139000',
      '国有集团',
      'B',
      '',
      '',
      '',
      ''
    ]);
  }

  if (options.badDateFormat) {
    // 第 4 行：日期格式错误
    wsData.push([
      '测试标讯-日期格式错',
      '测试招标代理3',
      '测试采购单位3',
      '广州',
      '2026/12/31 17:00', // 错误格式
      '不合法日期',
      '联系人3',
      '13700137000',
      'KA 客户',
      'C',
      '2000000',
      '软件',
      '格式错误测试',
      ''
    ]);
  }

  const ws = XLSX.utils.aoa_to_sheet(wsData);
  XLSX.utils.book_append_sheet(wb, ws, '标讯导入');

  const buf = XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
  return buf;
}

export const BIDDING_IMPORT_HEADERS = HEADERS;
