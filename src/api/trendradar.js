/**
 * TrendRadar API 调用模块
 * 用于获取跨平台热点趋势和市场洞察数据
 * 支持 MRO 工业品分类监控
 */

const API_BASE_URL = 'http://localhost:8080/api'

// 政治敏感词过滤列表
const POLITICS_FILTER_KEYWORDS = [
  // 政治人物
  '特朗普', '拜登', '普京', '泽连', '奥巴马', '默克尔', '马克龙', '苏纳克',
  '伊总统', '空袭', '袭击', '战争', '冲突', '军事', '军队', '士兵',
  // 军事/地缘
  '东部战区', '战区', '导弹', '武器', '军演', '南海', '台海', '俄乌', '巴以',
  '哈马斯', '以色列', '巴勒斯坦', '伊朗', '朝鲜', '韩国军队', '美军', '解放军',
  '国防部', '五角大楼', '北约', '联合国安理会', '制裁',
  // 政治事件
  '选举', '投票', '总统候选人', '国会', '议会', '政府换届', '政治危机',
  '示威', '抗议', '暴乱', '政变', '革命',
  // 其他敏感词
  '间谍', '渗透', '泄密', '情报', '特务',
  '恐怖袭击', '恐袭', '爆炸案', '袭击事件'
]

/**
 * 过滤政治敏感话题
 * @param {Array} topics - 原始话题列表
 * @returns {Array} 过滤后的话题
 */
export function filterPoliticsTopics(topics) {
  if (!topics || topics.length === 0) return []

  return topics.filter(topic => {
    const text = (topic.normalized_title + ' ' + (topic.sample_titles || []).join(' ')).toLowerCase()

    // 检查是否包含政治敏感词
    for (const keyword of POLITICS_FILTER_KEYWORDS) {
      if (text.includes(keyword.toLowerCase())) {
        return false // 过滤掉
      }
    }
    return true // 保留
  })
}

// MRO 工业品关键词分类（严格按照12大分类）
const MRO_KEYWORDS = {
  '工具': ['扳手', '钳子', '螺丝刀', '锤子', '卷尺', '电钻', '角磨机', '切割机', '电焊机', '氩弧焊'],
  '工具耗材': ['钻头', '锯片', '磨片', '砂纸', '刀头', '批头', '合金锯片', '砂轮片', '切割片', '焊丝', '焊条'],
  '焊接': ['电焊机', '氩弧焊', '点焊机', '激光焊', '焊枪', '焊接机器人', '气保焊'],
  '刀具': ['铣刀', '车刀', '钻头', '铰刀', '数控刀', '硬质合金'],
  '量具': ['游标卡尺', '千分尺', '百分表', '高度规', '粗糙度仪', '三坐标'],
  '机床': ['车床', '铣床', '磨床', '加工中心', '数控机床', 'CNC', '激光切割', '线切割'],
  '磨具': ['砂轮', '磨头', '油石', '金刚石', 'CBN', '研磨膏'],
  '润滑胶粘': ['润滑油', '润滑脂', '切削液', '防锈油', '结构胶', '密封胶', '环氧树脂', '硅胶', '热熔胶'],
  '车间化学品': ['清洗剂', '脱脂剂', '防锈剂', '冷却液', '发黑剂', '磷化液'],
  '劳保安全': ['安全帽', '防护眼镜', '防尘口罩', '安全鞋', '劳保鞋', '防护服', '手套', '安全带', '呼吸器'],
  '消防': ['灭火器', '消火栓', '消防水带', '消防泵', '烟感报警', '应急灯', '安全出口'],
  '搬运': ['叉车', '手推车', '搬运车', '液压车', '堆高机', 'AGV', '输送带', '起重机', '升降机'],
  '存储': ['货架', '货柜', '托盘', '周转箱', '零件盒', '仓储笼', '储物柜', '工具柜'],
  '工位': ['工作台', '工具柜', '工装夹具', '流水线', '作业台'],
  '包材': ['包装箱', '纸箱', '打包带', '缠绕膜', '封箱胶带', '气泡膜', '护角', '钢带'],
  '清洁': ['扫把', '拖把', '清洁剂', '洗手液', '擦手纸', '垃圾袋', '吸尘器', '洗地机'],
  '办公': ['打印机', '复印机', '办公椅', '文件柜', '办公桌', '碎纸机', '装订机'],
  '制冷暖通': ['空调', '冷风机', '除湿机', '加湿器', '风机盘管', '新风机组', '冷却塔', '排气扇'],
  '工控低压': ['PLC', '变频器', '伺服电机', '步进电机', '传感器', '编码器', '断路器', '接触器', '继电器'],
  '电工照明': ['电线', '电缆', '开关', '插座', 'LED灯', '工矿灯', '路灯', '隧道灯', '防爆灯'],
  '轴承': ['深沟球轴承', '圆锥滚子轴承', '调心轴承', '直线轴承', '关节轴承', '轴承座'],
  '皮带': ['同步带', 'V带', '输送带', '三角带', '皮带轮', '链条', '链轮'],
  '机械电子': ['齿轮', '联轴器', '减速机', '链条', '链轮', '机械密封', '油封', 'O型圈', '垫片'],
  '气动': ['气缸', '气动阀', '气源处理器', '气管', '接头', '气动马达', '气动夹爪'],
  '液压管阀': ['液压泵', '液压阀', '油缸', '液压站', '球阀', '闸阀', '蝶阀', '截止阀', '电磁阀'],
  '泵': ['离心泵', '螺杆泵', '齿轮泵', '柱塞泵', '隔膜泵', '潜水泵', '化工泵'],
  '紧固': ['螺栓', '螺母', '螺丝', '垫圈', '膨胀螺丝', '钻尾丝', '自攻钉'],
  '密封': ['油封', 'O型圈', '垫片', '填料', '密封条', '骨架油封', '密封胶'],
  '建工材料': ['水泥', '钢材', '木材', '瓷砖', '涂料', '防水材料', '管材'],
  '工业检测': ['硬度计', '粗糙度仪', '探伤仪', '测温仪', '测厚仪', '振动仪', '平衡机'],
  '实验室产品': ['显微镜', '天平', '离心机', '烘箱', '培养箱', '振荡器', '移液器', '试剂瓶'],
  '企业福礼': ['节日礼品', '员工福利', '劳保福利', '健康体检', '团建活动', '年会礼品'],
  '紧急救护': ['急救包', '急救箱', 'AED', '除颤仪', '担架', '医用敷料', '呼吸器']
}

/**
 * 获取健康状态
 */
export async function getHealth() {
  try {
    const response = await fetch(`${API_BASE_URL}/health`)
    return await response.json()
  } catch (error) {
    console.error('TrendRadar API 连接失败:', error)
    throw error
  }
}

/**
 * 获取跨平台热点
 * @param {number} limit - 最大返回数量
 * @param {number} minPlatforms - 最少跨平台数
 */
export async function getBreakoutTopics(limit = 20, minPlatforms = 2) {
  try {
    const response = await fetch(
      `${API_BASE_URL}/trends/breakout?limit=${limit}&min_platforms=${minPlatforms}`
    )
    if (!response.ok) throw new Error('获取热点失败')
    return await response.json()
  } catch (error) {
    console.error('获取跨平台热点失败:', error)
    return []
  }
}

/**
 * 获取所有话题
 * @param {number} limit - 最大返回数量
 */
export async function getAllTopics(limit = 50) {
  try {
    const response = await fetch(`${API_BASE_URL}/trends/all?limit=${limit}`)
    if (!response.ok) throw new Error('获取话题失败')
    return await response.json()
  } catch (error) {
    console.error('获取所有话题失败:', error)
    return []
  }
}

/**
 * 获取科技类话题（使用频率词过滤）
 * @param {number} limit - 最大返回数量
 */
export async function getTechTopics(limit = 50) {
  try {
    const response = await fetch(`${API_BASE_URL}/trends/tech?limit=${limit}`)
    if (!response.ok) throw new Error('获取科技话题失败')
    return await response.json()
  } catch (error) {
    console.error('获取科技话题失败:', error)
    return []
  }
}

/**
 * 过滤 MRO 工业品相关话题
 * @param {Array} topics - 原始话题列表
 * @returns {Array} MRO 相关话题
 */
export function filterMROTopics(topics) {
  if (!topics || topics.length === 0) return []

  return topics.filter(topic => {
    const title = topic.normalized_title.toLowerCase()
    const samples = topic.sample_titles || []

    // 检查标题和样本标题是否包含 MRO 关键词
    for (const [category, keywords] of Object.entries(MRO_KEYWORDS)) {
      for (const kw of keywords) {
        if (title.includes(kw.toLowerCase())) {
          topic.mroCategory = category
          return true
        }
        for (const sample of samples) {
          if (sample.toLowerCase().includes(kw.toLowerCase())) {
            topic.mroCategory = category
            return true
          }
        }
      }
    }
    return false
  })
}

/**
 * 获取 MRO 工业品热点
 * @param {number} limit - 最大返回数量
 */
export async function getMROTopics(limit = 30) {
  const allTopics = await getAllTopics(limit * 2) // 获取更多数据以便过滤
  return filterMROTopics(allTopics).slice(0, limit)
}

/**
 * 获取平台重叠矩阵
 */
export async function getPlatformMatrix() {
  try {
    const response = await fetch(`${API_BASE_URL}/platforms/matrix`)
    if (!response.ok) throw new Error('获取平台矩阵失败')
    return await response.json()
  } catch (error) {
    console.error('获取平台矩阵失败:', error)
    return { platforms: [], matrix: {}, total_overlaps: 0 }
  }
}

/**
 * 获取统计摘要
 */
export async function getStatsSummary() {
  try {
    const response = await fetch(`${API_BASE_URL}/stats/summary`)
    if (!response.ok) throw new Error('获取统计失败')
    return await response.json()
  } catch (error) {
    console.error('获取统计摘要失败:', error)
    return null
  }
}

/**
 * 清除缓存
 */
export async function clearCache() {
  try {
    const response = await fetch(`${API_BASE_URL}/cache/clear`, { method: 'POST' })
    return await response.json()
  } catch (error) {
    console.error('清除缓存失败:', error)
    return null
  }
}

/**
 * 将 TrendRadar 热点数据转换为投标系统可用的行业趋势数据
 * 优先显示 MRO 相关内容，自动过滤政治敏感内容
 * 保证始终返回完整的30个MRO行业
 */
export function transformToIndustryTrends(topics, useMRO = true, fallbackData = null) {
  // 没有话题时，返回调用方传入的 fallback（或默认 MRO 30 个行业）
  if (!topics || topics.length === 0) {
    return fallbackData || getDefaultMROTrends()
  }

  // 先过滤掉政治敏感话题
  topics = filterPoliticsTopics(topics)

  // 如果启用 MRO 过滤，先提取 MRO 相关话题
  let mroTopics
  let otherTopics

  if (useMRO) {
    mroTopics = filterMROTopics(topics)
    otherTopics = topics.filter(t => !t.mroCategory)
  } else {
    otherTopics = topics
  }

  // 行业分类映射（扩展包含 MRO 分类）
  const industryKeywords = {
    ...MRO_KEYWORDS,
    '数据中心': ['服务器', '机房', '算力', '云计算', '大数据'],
    '医疗设备': ['医疗', '医院', '影像', '诊断'],
    '智慧城市': ['智慧', '城管', '交通', '安防'],
    '教育信息化': ['教育', '学校', '教学', '校园'],
    '环保设备': ['环保', '污水', '监测', '治理'],
    '软件服务': ['软件', '系统', '平台', '开发'],
  }

  const trends = []
  const usedIndustries = new Set()

  // 合并：MRO 话题优先，然后是其他话题
  const combinedTopics = [
    ...mroTopics.sort((a, b) => b.momentum_score - a.momentum_score),
    ...otherTopics.sort((a, b) => b.platform_count - a.platform_count || b.momentum_score - a.momentum_score)
  ]

  for (const topic of combinedTopics) {
    // 如果已有 MRO 分类，直接使用
    let industry = topic.mroCategory || null

    // 否则根据关键词匹配行业
    if (!industry) {
      industry = '综合类'
      for (const [key, keywords] of Object.entries(industryKeywords)) {
        if (keywords.some(kw => topic.normalized_title.includes(kw))) {
          industry = key
          break
        }
      }
    }

    // 避免重复行业
    if (usedIndustries.has(industry)) continue
    usedIndustries.add(industry)

    trends.push({
      industry,
      count: topic.total_appearances * 10,
      amount: Math.floor(topic.total_appearances * 300),
      growth: Math.floor(topic.momentum_score * 10),
      trend: topic.momentum_score > 0.5 ? 'up' : topic.momentum_score > 0.2 ? 'stable' : 'down',
      hotLevel: Math.min(5, Math.ceil(topic.breakout_score / 50)),
      color: getIndustryColor(industry),
      source: topic,
      isMRO: !!topic.mroCategory,
      fromRealData: true
    })
  }

  // 如果真实数据不足30个，补充默认 MRO 数据
  const defaultTrends = getDefaultMROTrends()
  for (const defaultTrend of defaultTrends) {
    if (trends.length >= 30) break
    if (!usedIndustries.has(defaultTrend.industry)) {
      trends.push({ ...defaultTrend, fromRealData: false })
      usedIndustries.add(defaultTrend.industry)
    }
  }

  return trends
}

/**
 * 获取默认的MRO行业趋势数据（严格按照12大分类）
 */
function getDefaultMROTrends() {
  return [
    // 1. 工具、工具耗材、焊接
    { industry: '工具', count: 331, amount: 21100, growth: 32, trend: 'up', hotLevel: 5, color: 'blue' },
    { industry: '工具耗材', count: 268, amount: 8600, growth: 18, trend: 'up', hotLevel: 4, color: 'blue' },
    { industry: '焊接', count: 98, amount: 15800, growth: 22, trend: 'up', hotLevel: 4, color: 'blue' },
    // 2. 刀具、量具、机床、磨具
    { industry: '刀具', count: 112, amount: 18600, growth: 18, trend: 'up', hotLevel: 4, color: 'green' },
    { industry: '量具', count: 87, amount: 12400, growth: 15, trend: 'stable', hotLevel: 3, color: 'green' },
    { industry: '机床', count: 76, amount: 38500, growth: 42, trend: 'up', hotLevel: 5, color: 'green' },
    { industry: '磨具', count: 94, amount: 9800, growth: 12, trend: 'stable', hotLevel: 3, color: 'green' },
    // 3. 润滑胶粘、车间化学品
    { industry: '润滑胶粘', count: 284, amount: 19800, growth: 15, trend: 'up', hotLevel: 4, color: 'orange' },
    { industry: '车间化学品', count: 72, amount: 6400, growth: 5, trend: 'stable', hotLevel: 3, color: 'orange' },
    // 4. 劳保安全、消防
    { industry: '劳保安全', count: 413, amount: 37600, growth: 42, trend: 'up', hotLevel: 5, color: 'red' },
    { industry: '消防', count: 134, amount: 18500, growth: 32, trend: 'up', hotLevel: 4, color: 'red' },
    // 5. 搬运、存储、工位、包材
    { industry: '搬运', count: 92, amount: 28600, growth: 25, trend: 'up', hotLevel: 4, color: 'purple' },
    { industry: '存储', count: 178, amount: 16800, growth: 20, trend: 'up', hotLevel: 4, color: 'purple' },
    { industry: '工位', count: 115, amount: 8900, growth: 12, trend: 'stable', hotLevel: 3, color: 'purple' },
    { industry: '包材', count: 203, amount: 12400, growth: 15, trend: 'up', hotLevel: 4, color: 'purple' },
    // 6. 清洁、办公、制冷暖通
    { industry: '清洁', count: 167, amount: 7800, growth: 10, trend: 'stable', hotLevel: 3, color: 'cyan' },
    { industry: '办公', count: 289, amount: 18600, growth: 8, trend: 'stable', hotLevel: 3, color: 'cyan' },
    { industry: '制冷暖通', count: 223, amount: 53300, growth: 25, trend: 'up', hotLevel: 4, color: 'cyan' },
    // 7. 工控低压电工照明
    { industry: '工控低压', count: 333, amount: 57600, growth: 30, trend: 'up', hotLevel: 5, color: 'yellow' },
    { industry: '电工照明', count: 410, amount: 36300, growth: 24, trend: 'up', hotLevel: 4, color: 'yellow' },
    // 8. 轴承、皮带、机械、电子
    { industry: '轴承', count: 142, amount: 24500, growth: 20, trend: 'up', hotLevel: 4, color: 'pink' },
    { industry: '皮带', count: 98, amount: 11200, growth: 12, trend: 'stable', hotLevel: 3, color: 'pink' },
    { industry: '机械电子', count: 268, amount: 32000, growth: 28, trend: 'up', hotLevel: 4, color: 'pink' },
    // 9. 气动、液压管阀、泵
    { industry: '气动', count: 126, amount: 18500, growth: 22, trend: 'up', hotLevel: 4, color: 'indigo' },
    { industry: '液压管阀', count: 264, amount: 60500, growth: 26, trend: 'up', hotLevel: 4, color: 'indigo' },
    { industry: '泵', count: 145, amount: 22000, growth: 18, trend: 'up', hotLevel: 4, color: 'indigo' },
    // 10. 紧固、密封、建工材料
    { industry: '紧固', count: 268, amount: 14500, growth: 12, trend: 'stable', hotLevel: 3, color: 'lime' },
    { industry: '密封', count: 135, amount: 9800, growth: 10, trend: 'stable', hotLevel: 3, color: 'lime' },
    { industry: '建工材料', count: 178, amount: 22000, growth: 18, trend: 'up', hotLevel: 4, color: 'lime' },
    // 11. 工业检测、实验室产品
    { industry: '工业检测', count: 86, amount: 28600, growth: 30, trend: 'up', hotLevel: 4, color: 'teal' },
    { industry: '实验室产品', count: 72, amount: 24500, growth: 25, trend: 'up', hotLevel: 4, color: 'teal' },
    // 12. 企业福礼、紧急救护
    { industry: '企业福礼', count: 312, amount: 9600, growth: 5, trend: 'stable', hotLevel: 3, color: 'grey' },
    { industry: '紧急救护', count: 145, amount: 12800, growth: 15, trend: 'up', hotLevel: 3, color: 'grey' }
  ]
}

/**
 * 获取行业对应颜色（严格按照12大分类）
 */
function getIndustryColor(industry) {
  const colorMap = {
    // 1. 工具、工具耗材、焊接
    '工具': 'blue',
    '工具耗材': 'blue',
    '焊接': 'blue',
    // 2. 刀具、量具、机床、磨具
    '刀具': 'green',
    '量具': 'green',
    '机床': 'green',
    '磨具': 'green',
    // 3. 润滑胶粘、车间化学品
    '润滑胶粘': 'orange',
    '车间化学品': 'orange',
    // 4. 劳保安全、消防
    '劳保安全': 'red',
    '消防': 'red',
    // 5. 搬运、存储、工位、包材
    '搬运': 'purple',
    '存储': 'purple',
    '工位': 'purple',
    '包材': 'purple',
    // 6. 清洁、办公、制冷暖通
    '清洁': 'cyan',
    '办公': 'cyan',
    '制冷暖通': 'cyan',
    // 7. 工控低压电工照明
    '工控低压': 'yellow',
    '电工照明': 'yellow',
    // 8. 轴承、皮带、机械、电子
    '轴承': 'pink',
    '皮带': 'pink',
    '机械电子': 'pink',
    // 9. 气动、液压管阀、泵
    '气动': 'indigo',
    '液压管阀': 'indigo',
    '泵': 'indigo',
    // 10. 紧固、密封、建工材料
    '紧固': 'lime',
    '密封': 'lime',
    '建工材料': 'lime',
    // 11. 工业检测、实验室产品
    '工业检测': 'teal',
    '实验室产品': 'teal',
    // 12. 企业福礼、紧急救护
    '企业福礼': 'grey',
    '紧急救护': 'grey'
  }
  return colorMap[industry] || 'blue'
}

/**
 * 将 TrendRadar 热点转换为高潜力机会
 * 优先显示 MRO 相关内容，自动过滤政治敏感内容
 */
export function transformToOpportunities(topics, useMRO = true) {
  if (!topics || topics.length === 0) return []

  // 先过滤掉政治敏感话题
  topics = filterPoliticsTopics(topics)

  let mroTopics
  let otherTopics

  if (useMRO) {
    mroTopics = filterMROTopics(topics)
    otherTopics = topics.filter(t => !t.mroCategory)
  } else {
    otherTopics = topics
  }

  const combinedTopics = [
    ...mroTopics.slice(0, 3),
    ...otherTopics.slice(0, 3)
  ].slice(0, 6)

  return combinedTopics.map((topic, index) => {
    const priority = topic.breakout_score > 0.8 ? 'high' : topic.breakout_score > 0.5 ? 'medium' : 'normal'
    const match = Math.min(95, 60 + Math.floor(topic.momentum_score * 35))

    return {
      id: `tr-${index}`,
      title: topic.normalized_title,
      priority,
      purchaser: topic.platforms.join(', '),
      budget: Math.floor(topic.total_appearances * 50),
      region: getRegionFromTitle(topic.normalized_title),
      reason: topic.mroCategory
        ? `MRO「${topic.mroCategory}」相关，${topic.platform_count}个平台出现，动量${topic.momentum_score.toFixed(1)}`
        : `该话题在 ${topic.platform_count} 个平台出现，动量评分 ${topic.momentum_score.toFixed(2)}`,
      match,
      source: topic,
      isMRO: !!topic.mroCategory,
      mroCategory: topic.mroCategory
    }
  })
}

/**
 * 从标题推断地区
 */
function getRegionFromTitle(title) {
  const regionMap = {
    '北京': '华北', '上海': '华东', '深圳': '华南', '广州': '华南',
    '杭州': '华东', '成都': '西南', '武汉': '华中', '西安': '西北'
  }
  for (const [city, region] of Object.entries(regionMap)) {
    if (title.includes(city)) return region
  }
  return '全国'
}

/**
 * 生成 AI 洞察文本
 * 包含 MRO 相关信息
 */
export function generateInsight(topics, stats) {
  if (!topics || topics.length === 0) {
    return '当前暂无实时热点数据，请稍后刷新。'
  }

  const mroTopics = filterMROTopics(topics)

  let insight = `监测到 ${stats?.total_topics || topics.length} 个跨平台热点话题。`

  if (mroTopics.length > 0) {
    insight += `其中 ${mroTopics.length} 条与 MRO 工业品相关。`
    const categories = [...new Set(mroTopics.map(t => t.mroCategory))]
    if (categories.length > 0) {
      insight += `热门品类：${categories.slice(0, 3).join('、')}。`
    }
  }

  const topTopics = topics.slice(0, 3)
  if (topTopics.length > 0) {
    const avgMomentum = (topTopics.reduce((sum, t) => sum + t.momentum_score, 0) / topTopics.length).toFixed(2)
    insight += ` 当前热度最高的是「${topTopics[0].normalized_title}」，平均动量指数 ${avgMomentum}。`

    if (avgMomentum > 10) {
      insight += ' 建议重点关注相关领域投标机会。'
    }
  }

  return insight
}

/**
 * 生成预测建议
 * 包含 MRO 相关建议
 */
export function generateForecastTips(topics) {
  const mroTopics = filterMROTopics(topics)

  const tips = [
    { text: '关注跨平台热点话题的延伸需求，提前准备相关资质', color: '#67c23a' },
    { text: '高动量话题对应的行业预计未来2周内将有更多招标发布', color: '#409eff' },
    { text: '建议优先跟进平台覆盖数≥3的热点相关项目', color: '#e6a23c' }
  ]

  if (mroTopics.length > 0) {
    tips.unshift({
      text: `检测到 ${mroTopics.length} 条 MRO 工业品热点，建议关注相关品类采购趋势`,
      color: '#f56c6c'
    })
  } else if (topics.length > 0) {
    const topTopic = topics[0]
    tips.unshift({
      text: `当前热点「${topTopic.normalized_title}」相关项目值得关注`,
      color: '#f56c6c'
    })
  }

  return tips.slice(0, 4)
}

/**
 * 获取 MRO 分类统计
 */
export function getMROCategoryStats(topics) {
  const mroTopics = filterMROTopics(topics)
  const stats = {}

  for (const topic of mroTopics) {
    const cat = topic.mroCategory || '其他'
    if (!stats[cat]) {
      stats[cat] = { count: 0, topics: [] }
    }
    stats[cat].count++
    stats[cat].topics.push(topic)
  }

  return stats
}
