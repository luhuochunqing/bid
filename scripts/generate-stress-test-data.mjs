// Input: CLI arguments (--tenders <count>, --projects <count>, --cases <count>, --out <filepath>, --batch <tag>)
// Output: MySQL 8.0 compatible seed SQL file for stress testing and companion rollback SQL script
// Pos: scripts/ - Stress testing data generation utility
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import fs from 'node:fs'
import path from 'node:path'
import process from 'node:process'

const args = process.argv.slice(2)

function getArgValue(flag, defaultValue) {
  const index = args.indexOf(flag)
  if (index !== -1 && index + 1 < args.length) {
    return args[index + 1]
  }
  return defaultValue
}

const tenderCount = parseInt(getArgValue('--tenders', '10000'), 10)
const projectCount = parseInt(getArgValue('--projects', '2000'), 10)
const caseCount = parseInt(getArgValue('--cases', '500'), 10)
const outFilePath = path.resolve(process.cwd(), getArgValue('--out', 'stress-test-seed-data.sql'))
const cleanupFilePath = path.resolve(path.dirname(outFilePath), 'cleanup-stress-test-data.sql')
const batchTag = getArgValue('--batch', 'STRESS_' + new Date().toISOString().slice(0, 10).replace(/-/g, ''))

console.log(`[Data Generator] Generating Stress Test Data:`)
console.log(` - Tenders:  ${tenderCount}`)
console.log(` - Projects: ${projectCount}`)
console.log(` - Cases:    ${caseCount}`)
console.log(` - Batch:    ${batchTag}`)
console.log(` - Output:   ${outFilePath}`)
console.log(` - Cleanup:  ${cleanupFilePath}`)

const industries = [
  '智慧园区', '电力能源', '数据中心', '智能制造', '轨道交通',
  '智慧城市', '医疗卫生', '金融科技', '水利水务', '公共安全',
  '石油化工', '航空航天', '新能源汽车', '教育科研', '环保节能'
]

const regions = [
  '上海', '北京', '广州', '深圳', '成都', '武汉', '杭州', '南京',
  '西安', '合肥', '重庆', '长沙', '郑州', '天津', '苏州', '福州'
]

const purchaserPrefixes = [
  '国家电网', '中国移动', '中国电信', '中国建筑', '中国中铁',
  '华为技术', '比亚迪', '腾讯科技', '阿里巴巴', '上汽集团',
  '北京大学', '清华大学', '上海交通大学', '中国铁建', '中石油'
]

const purchaserSuffixes = [
  '集团有限公司', '股份有限公司', '管理委员会', '采购中心', '信息化部', '运营管理分公司'
]

const tenderTypes = ['物资采购', '框架协议', '系统集成', '软件研发', '设备集采', '运维服务']
const tenderStatuses = ['PENDING_ASSIGNMENT', 'TRACKING', 'EVALUATED', 'BIDDING', 'WON', 'LOST', 'ABANDONED']
const projectStatuses = ['INITIATED', 'BIDDING', 'EVALUATING', 'WON', 'LOST', 'ABANDONED']
const projectStages = ['INITIATED', 'DRAFTING', 'SEAL_PENDING', 'RESULT_PENDING', 'EVALUATING', 'CLOSED']
const customerTypes = ['政府', '央企', '国企', '民企', '事业单位']
const priorities = ['S', 'A', 'B', 'C']
const sources = ['中国政府采购网', '各省招标网', '企业招标平台', '南方电网采购平台', '比亚迪供应商平台', '人工录入']
const managerNames = ['张经理', '李主管', '王经理', '陈经理', '刘主管', '赵经理', '周主管']

function randomItem(arr) {
  return arr[Math.floor(Math.random() * arr.length)]
}

function randomInt(min, max) {
  return Math.floor(Math.random() * (max - min + 1)) + min
}

function randomBudget() {
  const tiers = [
    () => randomInt(10, 100) * 10000,
    () => randomInt(100, 500) * 10000,
    () => randomInt(500, 2000) * 10000,
    () => randomInt(2000, 8000) * 10000
  ]
  return randomItem(tiers)()
}

function escapeSql(str) {
  if (str === null || str === undefined) return 'NULL'
  return `'${String(str).replace(/'/g, "''").replace(/\\/g, '\\\\')}'`
}

function randomDate(startYear = 2025, endYear = 2026) {
  const start = new Date(`${startYear}-01-01T00:00:00Z`).getTime()
  const end = new Date(`${endYear}-12-31T23:59:59Z`).getTime()
  const d = new Date(start + Math.random() * (end - start))
  return d.toISOString().slice(0, 19).replace('T', ' ')
}

fs.mkdirSync(path.dirname(outFilePath), { recursive: true })
const writeStream = fs.createWriteStream(outFilePath, { encoding: 'utf8' })

writeStream.write(`-- ==========================================================================\n`)
writeStream.write(`-- 西域数智化投标管理平台 - 压力测试铺底仿真数据\n`)
writeStream.write(`-- 生成时间: ${new Date().toISOString()}\n`)
writeStream.write(`-- 批次标识: ${batchTag}\n`)
writeStream.write(`-- 包含规模: 标讯 ${tenderCount} 条, 项目 ${projectCount} 条, 案例 ${caseCount} 条\n`)
writeStream.write(`-- ==========================================================================\n\n`)
writeStream.write(`SET NAMES utf8mb4;\n`)
writeStream.write(`SET FOREIGN_KEY_CHECKS = 0;\n\n`)

// 1. 生成 Tenders 数据 (按 500 条分批 INSERT)
console.log(` -> Writing Tenders...`)
const tenderBatchSize = 500
let tenderIdStart = 100000

for (let i = 0; i < tenderCount; i += tenderBatchSize) {
  const currentBatch = Math.min(tenderBatchSize, tenderCount - i)
  writeStream.write(`INSERT INTO \`tenders\` (\n`)
  writeStream.write(`  \`id\`, \`title\`, \`budget\`, \`region\`, \`industry\`, \`purchaser_name\`,\n`)
  writeStream.write(`  \`publish_date\`, \`deadline\`, \`bid_opening_time\`, \`registration_deadline\`,\n`)
  writeStream.write(`  \`status\`, \`customer_type\`, \`priority\`, \`source_type\`, \`source_platform\`, \`source\`,\n`)
  writeStream.write(`  \`description\`, \`tags\`, \`created_at\`, \`updated_at\`, \`version\`\n`)
  writeStream.write(`) VALUES\n`)

  const rows = []
  for (let j = 0; j < currentBatch; j++) {
    const id = tenderIdStart + i + j
    const industry = randomItem(industries)
    const region = randomItem(regions)
    const type = randomItem(tenderTypes)
    const purchaser = `${randomItem(purchaserPrefixes)}${region}${randomItem(purchaserSuffixes)}`
    const title = `[压测基准] ${region}${industry}2026年度${type}项目-${id}`
    const budget = randomBudget()
    const publishDate = randomDate(2025, 2026)
    const deadline = randomDate(2026, 2026)
    const bidOpening = deadline
    const regDeadline = publishDate
    const status = randomItem(tenderStatuses)
    const customerType = randomItem(customerTypes)
    const priority = randomItem(priorities)
    const source = randomItem(sources)
    const tags = `${industry},${type},${region},${batchTag}`
    const desc = `用于西域测试环境压力测试的仿真标讯数据，项目涵盖${industry}领域的${type}。采购主体为${purchaser}，预算金额为人民币${(budget / 10000).toFixed(2)}万元。`
    const createdAt = publishDate
    const updatedAt = publishDate

    rows.push(`  (${id}, ${escapeSql(title)}, ${budget.toFixed(2)}, ${escapeSql(region)}, ${escapeSql(industry)}, ${escapeSql(purchaser)}, ${escapeSql(publishDate.slice(0, 10))}, ${escapeSql(deadline)}, ${escapeSql(bidOpening)}, ${escapeSql(regDeadline)}, ${escapeSql(status)}, ${escapeSql(customerType)}, ${escapeSql(priority)}, 'EXTERNAL_PLATFORM', ${escapeSql(source)}, ${escapeSql(source)}, ${escapeSql(desc)}, ${escapeSql(tags)}, ${escapeSql(createdAt)}, ${escapeSql(updatedAt)}, 0)`)
  }

  writeStream.write(rows.join(',\n') + ';\n\n')
}

// 2. 生成 Projects 及关联详情
console.log(` -> Writing Projects & Initiation Details...`)
const projectBatchSize = 500
const projectIdStart = 100000

for (let i = 0; i < projectCount; i += projectBatchSize) {
  const currentBatch = Math.min(projectBatchSize, projectCount - i)
  writeStream.write(`INSERT INTO \`projects\` (\n`)
  writeStream.write(`  \`id\`, \`tender_id\`, \`name\`, \`budget\`, \`industry\`, \`region\`, \`customer\`,\n`)
  writeStream.write(`  \`status\`, \`customer_type\`, \`stage\`, \`manager_id\`, \`description\`, \`tags_json\`,\n`)
  writeStream.write(`  \`created_at\`, \`updated_at\`\n`)
  writeStream.write(`) VALUES\n`)

  const projRows = []
  for (let j = 0; j < currentBatch; j++) {
    const id = projectIdStart + i + j
    const linkedTenderId = tenderIdStart + (i + j) % tenderCount
    const industry = randomItem(industries)
    const region = randomItem(regions)
    const type = randomItem(tenderTypes)
    const customer = `${randomItem(purchaserPrefixes)}${region}${randomItem(purchaserSuffixes)}`
    const name = `[压测基准] ${region}${customer}${industry}${type}项目`
    const budget = randomBudget()
    const status = randomItem(projectStatuses)
    const stage = randomItem(projectStages)
    const customerType = randomItem(customerTypes)
    const managerId = randomInt(1, 10)
    const desc = `压力测试生成的项目数据，对应标讯ID: ${linkedTenderId}`
    const tagsJson = JSON.stringify([industry, region, batchTag])
    const createdAt = randomDate(2025, 2026)

    projRows.push(`  (${id}, ${linkedTenderId}, ${escapeSql(name)}, ${budget.toFixed(2)}, ${escapeSql(industry)}, ${escapeSql(region)}, ${escapeSql(customer)}, ${escapeSql(status)}, ${escapeSql(customerType)}, ${escapeSql(stage)}, ${managerId}, ${escapeSql(desc)}, ${escapeSql(tagsJson)}, ${escapeSql(createdAt)}, ${escapeSql(createdAt)})`)
  }

  writeStream.write(projRows.join(',\n') + ';\n\n')
}

// 3. 生成 Project Initiation Details
for (let i = 0; i < projectCount; i += projectBatchSize) {
  const currentBatch = Math.min(projectBatchSize, projectCount - i)
  writeStream.write(`INSERT INTO \`project_initiation_details\` (\n`)
  writeStream.write(`  \`id\`, \`project_id\`, \`bid_open_time\`, \`bid_month\`, \`customer_type\`, \`need_deposit\`,\n`)
  writeStream.write(`  \`owner_unit\`, \`project_type\`, \`review_status\`, \`customer_grade\`, \`bid_status\`,\n`)
  writeStream.write(`  \`bidding_leader_name\`, \`project_leader_name\`, \`leader_department\`,\n`)
  writeStream.write(`  \`created_at\`, \`updated_at\`\n`)
  writeStream.write(`) VALUES\n`)

  const detailRows = []
  for (let j = 0; j < currentBatch; j++) {
    const id = projectIdStart + i + j
    const projectId = id
    const bidOpenTime = randomDate(2026, 2026)
    const bidMonth = bidOpenTime.slice(0, 7)
    const customerType = randomItem(customerTypes)
    const needDeposit = randomItem(['YES', 'NO'])
    const ownerUnit = `西域供应链${randomItem(regions)}分公司`
    const projectType = randomItem(tenderTypes)
    const reviewStatus = randomItem(['DRAFT', 'APPROVED', 'SUBMITTED'])
    const customerGrade = randomItem(priorities)
    const bidStatus = randomItem(['PREPARING', 'BIDDING', 'WON', 'LOST'])
    const biddingLeader = randomItem(managerNames)
    const projectLeader = randomItem(managerNames)
    const dept = `${randomItem(regions)}事业部`
    const createdAt = randomDate(2025, 2026)

    detailRows.push(`  (${id}, ${projectId}, ${escapeSql(bidOpenTime)}, ${escapeSql(bidMonth)}, ${escapeSql(customerType)}, ${escapeSql(needDeposit)}, ${escapeSql(ownerUnit)}, ${escapeSql(projectType)}, ${escapeSql(reviewStatus)}, ${escapeSql(customerGrade)}, ${escapeSql(bidStatus)}, ${escapeSql(biddingLeader)}, ${escapeSql(projectLeader)}, ${escapeSql(dept)}, ${escapeSql(createdAt)}, ${escapeSql(createdAt)})`)
  }

  writeStream.write(detailRows.join(',\n') + ';\n\n')
}

// 4. 生成 Cases (知识库案例)
console.log(` -> Writing Knowledge Cases...`)
const caseBatchSize = 250
const caseIdStart = 100000

for (let i = 0; i < caseCount; i += caseBatchSize) {
  const currentBatch = Math.min(caseBatchSize, caseCount - i)
  writeStream.write(`INSERT INTO \`cases\` (\n`)
  writeStream.write(`  \`id\`, \`title\`, \`industry\`, \`customer\`, \`amount\`, \`project_date\`,\n`)
  writeStream.write(`  \`description\`, \`tags\`, \`created_at\`, \`updated_at\`\n`)
  writeStream.write(`) VALUES\n`)

  const caseRows = []
  for (let j = 0; j < currentBatch; j++) {
    const id = caseIdStart + i + j
    const industry = randomItem(industries)
    const region = randomItem(regions)
    const title = `[压测基准] ${region}${industry}标杆成功案例-${id}`
    const customer = `${randomItem(purchaserPrefixes)}${region}集团`
    const amount = randomBudget()
    const projectDate = randomDate(2024, 2025).slice(0, 10)
    const tags = `${industry},成功案例,${batchTag}`
    const desc = `用于压力测试知识库检索的基准案例数据。系统在${industry}行业提供全面解决方案，合同金额${(amount / 10000).toFixed(2)}万元。`
    const createdAt = randomDate(2025, 2026)

    caseRows.push(`  (${id}, ${escapeSql(title)}, ${escapeSql(industry)}, ${escapeSql(customer)}, ${amount.toFixed(2)}, ${escapeSql(projectDate)}, ${escapeSql(desc)}, ${escapeSql(tags)}, ${escapeSql(createdAt)}, ${escapeSql(createdAt)})`)
  }

  writeStream.write(caseRows.join(',\n') + ';\n\n')
}

writeStream.write(`SET FOREIGN_KEY_CHECKS = 1;\n`)
writeStream.write(`-- 压测数据生成完毕\n`)
writeStream.end()

// 生成一键清理脚本
const cleanupContent = `-- ==========================================================================\n` +
`-- 西域数智化投标管理平台 - 压测铺底数据一键清理脚本\n` +
`-- 生成时间: ${new Date().toISOString()}\n` +
`-- ==========================================================================\n\n` +
`SET FOREIGN_KEY_CHECKS = 0;\n\n` +
`-- 1. 清理项目关联表\n` +
`DELETE FROM \`project_evaluation\` WHERE \`project_id\` >= 100000 OR \`project_id\` IN (SELECT \`id\` FROM \`projects\` WHERE \`name\` LIKE '[压测基准]%' OR \`tags_json\` LIKE '%${batchTag}%');\n` +
`DELETE FROM \`project_retrospective\` WHERE \`project_id\` >= 100000 OR \`project_id\` IN (SELECT \`id\` FROM \`projects\` WHERE \`name\` LIKE '[压测基准]%' OR \`tags_json\` LIKE '%${batchTag}%');\n` +
`DELETE FROM \`project_initiation_details\` WHERE \`id\` >= 100000 OR \`project_id\` >= 100000;\n\n` +
`-- 2. 清理主业务表\n` +
`DELETE FROM \`projects\` WHERE \`id\` >= 100000 OR \`name\` LIKE '[压测基准]%' OR \`tags_json\` LIKE '%${batchTag}%';\n` +
`DELETE FROM \`tenders\` WHERE \`id\` >= 100000 OR \`title\` LIKE '[压测基准]%' OR \`tags\` LIKE '%${batchTag}%';\n` +
`DELETE FROM \`cases\` WHERE \`id\` >= 100000 OR \`title\` LIKE '[压测基准]%' OR \`tags\` LIKE '%${batchTag}%';\n\n` +
`SET FOREIGN_KEY_CHECKS = 1;\n` +
`-- 压测数据清理完毕\n`

fs.writeFileSync(cleanupFilePath, cleanupContent, 'utf8')

console.log(`\n[Data Generator] ✅ Generation Complete!`)
console.log(` - Seed SQL:    ${outFilePath}`)
console.log(` - Cleanup SQL: ${cleanupFilePath}`)
console.log(`\n[Execution Tip]:`)
console.log(`  Import:  mysql -h <host> -u <user> -p <dbname> < ${path.basename(outFilePath)}`)
console.log(`  Cleanup: mysql -h <host> -u <user> -p <dbname> < ${path.basename(cleanupFilePath)}`)
