/**
 * AI 功能配置
 * 定义投标准备、标书编制、团队协作三大类别的 AI 功能
 */

export const aiConfigs = {
  // ==================== 投标准备 ====================

  'ai-analysis': {
    id: 'ai-analysis',
    name: 'AI 分析',
    icon: 'AnalysisIcon',
    category: 'prepare',
    enabled: true,
    description: '智能分析招标文件，提取关键信息和风险点',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是一位资深的投标专家，具有15年以上的投标经验',
      task: '请分析以下招标文件内容：\n{docContent}\n\n提取关键信息包括：项目背景、技术要求、商务条款、评分标准、潜在风险点',
      outputFormat: 'JSON格式，包含projectInfo, requirements, scoringCriteria, risks字段'
    },
    formConfig: {
      winRateWeights: {
        technical: 0.5,
        commercial: 0.3,
        experience: 0.2
      },
      riskThresholds: {
        high: 0.8,
        medium: 0.5,
        low: 0.2
      }
    }
  },

  'score-coverage': {
    id: 'score-coverage',
    name: '评分点覆盖',
    icon: 'CoverageIcon',
    category: 'prepare',
    enabled: true,
    description: '对标书评分点进行智能匹配和覆盖分析',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是投标评审专家，精通各类招标文件的评分标准',
      task: '根据以下评分标准分析投标方案的覆盖情况：\n{scoringCriteria}\n\n当前投标内容：\n{bidContent}\n\n指出已覆盖、部分覆盖、未覆盖的评分点',
      outputFormat: 'JSON格式，包含covered, partialCovered, uncovered字段，每个项包含point和score'
    },
    formConfig: {
      scoringItems: [],
      coverageThreshold: 0.8,
      highlightMissing: true
    }
  },

  'competition-intel': {
    id: 'competition-intel',
    name: '竞争情报',
    icon: 'IntelIcon',
    category: 'prepare',
    enabled: true,
    description: '分析竞争对手历史投标数据和策略',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是市场竞争分析专家，擅长从公开数据中提取竞争情报',
      task: '分析以下竞争对手的历史投标数据：\n{competitorData}\n\n项目信息：\n{projectInfo}\n\n预测竞争对手可能的报价策略和技术方案重点',
      outputFormat: 'JSON格式，包含competitors数组，每个含name, probabilityStrategy, priceRange, strengthAnalysis'
    },
    formConfig: {
      competitors: [],
      analysisDepth: 'deep',
      includeHistorical: true
    }
  },

  'roi-analysis': {
    id: 'roi-analysis',
    name: 'ROI 核算',
    icon: 'RoiIcon',
    category: 'prepare',
    enabled: true,
    description: '智能核算投标投入产出比，辅助决策',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是财务分析专家，精通项目成本核算和ROI分析',
      task: '根据以下信息进行投标ROI分析：\n项目预算：{budget}\n预计中标金额：{expectedRevenue}\n投入成本（人力、时间、资源）：{costs}\n历史中标率：{winRate}\n\n计算并给出投标建议',
      outputFormat: 'JSON格式，包含roi, paybackPeriod, recommendation, riskLevel字段'
    },
    formConfig: {
      costCategories: ['人力', '时间', '材料', '差旅', '其他'],
      roiThreshold: 1.5,
      currency: 'CNY'
    }
  },

  'market-timing': {
    id: 'market-timing',
    name: '商机时间预测',
    icon: 'TimingIcon',
    category: 'prepare',
    enabled: true,
    description: '基于历史投标数据预测下次招标时间窗口',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是投标市场分析专家，擅长从历史数据中预测招标时间',
      task: '基于以下业主的历史招标数据：\n{historicalData}\n\n分析招标频率和规律，预测下次招标时间窗口',
      outputFormat: 'JSON格式，包含nextTenderDate, confidence, analysis字段'
    },
    formConfig: {
      minHistoricalCount: 2,
      predictionWindow: 30,
      confidenceThreshold: 0.7
    }
  },

  // ==================== 标书编制 ====================

  'smart-assembly': {
    id: 'smart-assembly',
    name: '智能装配',
    icon: 'AssemblyIcon',
    category: 'composition',
    enabled: true,
    description: '根据招标要求智能组装标书各章节内容',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是标书编制专家，精通各类标书的结构和写作规范',
      task: '根据以下招标要求生成标书章节：\n招标要求：{requirements}\n\n章节类型：{sectionType}\n\n参考模板：\n{template}\n\n生成符合要求的章节内容',
      outputFormat: 'JSON格式，包含content, keyPoints, complianceNotes字段'
    },
    formConfig: {
      sections: [
        { id: 'executive-summary', name: ' executive摘要', required: true },
        { id: 'technical-solution', name: '技术方案', required: true },
        { id: 'commercial-proposal', name: '商务方案', required: true },
        { id: 'project-plan', name: '项目计划', required: true },
        { id: 'team-introduction', name: '团队介绍', required: false },
        { id: 'case-studies', name: '案例介绍', required: false }
      ],
      templates: {},
      autoFormat: true
    }
  },

  'solution-reuse': {
    id: 'solution-reuse',
    name: '历史方案提取与复用',
    icon: 'ReuseIcon',
    category: 'composition',
    enabled: true,
    description: '检索历史投标方案，智能匹配推荐、快速复用',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是投标方案知识库专家，熟悉所有历史投标方案内容',
      task: '根据当前项目需求：{projectRequirements}\n\n在历史方案库中搜索匹配度最高的投标方案，包括：技术方案、商务方案、评分响应等章节',
      outputFormat: 'JSON格式，包含matchedSolutions数组，每个含solutionId, title, projectName, matchScore, industry, contentSections'
    },
    formConfig: {
      matchThreshold: 0.6,
      maxResults: 20,
      sortBy: 'matchScore'
    }
  },

  'compliance-check': {
    id: 'compliance-check',
    name: '合规雷达',
    icon: 'ComplianceIcon',
    category: 'composition',
    enabled: true,
    description: '全方位检测标书合规性，规避废标风险',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是招投标法规专家，熟悉《招标投标法》及相关规定',
      task: '检查以下标书内容的合规性：\n{bidContent}\n\n招标文件要求：\n{requirements}\n\n逐项检查格式、内容、资质等合规性要求',
      outputFormat: 'JSON格式，包含compliant, issues数组（含severity, description, suggestion字段）'
    },
    formConfig: {
      checkItems: [
        { id: 'format', name: '格式检查', severity: 'medium' },
        { id: 'completeness', name: '完整性检查', severity: 'high' },
        { id: 'qualification', name: '资质检查', severity: 'high' },
        { id: 'signature', name: '签章检查', severity: 'high' },
        { id: 'deadline', name: '时效检查', severity: 'critical' },
        { id: 'pricing', name: '报价合规', severity: 'high' }
      ],
      autoFix: false,
      exportReport: true
    }
  },

  // ==================== 团队协作 ====================

  'version-control': {
    id: 'version-control',
    name: '版本管理',
    icon: 'VersionIcon',
    category: 'collaboration',
    enabled: true,
    description: '智能追踪标书版本变更，生成变更摘要',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是文档管理专家，擅长版本对比和变更追踪',
      task: '对比以下两个版本的标书内容：\n\n旧版本：\n{oldContent}\n\n新版本：\n{newContent}\n\n生成详细的变更摘要，包括新增、修改、删除的内容',
      outputFormat: 'JSON格式，包含summary, changes数组（含type, section, description字段）'
    },
    formConfig: {
      maxVersions: 10,
      autoSnapshot: true,
      mergeStrategy: 'smart'
    }
  },

  'collaboration-center': {
    id: 'collaboration-center',
    name: '协作中心',
    icon: 'CollaborationIcon',
    category: 'collaboration',
    enabled: true,
    description: '智能分配任务，追踪协作进度',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是项目管理专家，擅长团队协作和任务分配',
      task: '根据以下标书编制需求分配协作任务：\n项目需求：{requirements}\n\n团队成员：\n{teamMembers}\n\n截止时间：{deadline}\n\n生成合理的任务分配方案',
      outputFormat: 'JSON格式，包含tasks数组（含assignee, task, priority, deadline字段）'
    },
    formConfig: {
      teamRoles: ['项目经理', '技术负责人', '商务经理', '文档编写', '审核员'],
      priorityLevels: ['critical', 'high', 'medium', 'low'],
      notifyEnabled: true
    }
  },

  'auto-tasks': {
    id: 'auto-tasks',
    name: '自动化任务',
    icon: 'AutoTaskIcon',
    category: 'collaboration',
    enabled: true,
    description: '自动执行重复性标书编制任务',
    stats: { usage: 0, accuracy: 0 },
    promptTemplate: {
      role: '你是流程自动化专家，擅长识别和优化重复性任务',
      task: '根据以下标书模板和内容，识别可自动化执行的重复性任务：\n模板类型：{templateType}\n\n输入数据：\n{inputData}\n\n生成自动化执行方案',
      outputFormat: 'JSON格式，包含automatableTasks数组，每个含task, method, timeSaved字段'
    },
    formConfig: {
      taskTypes: [
        { id: 'fill-form', name: '表格填充', automatable: true },
        { id: 'format-text', name: '格式调整', automatable: true },
        { id: 'generate-section', name: '章节生成', automatable: true },
        { id: 'cross-reference', name: '交叉引用', automatable: true },
        { id: 'review', name: '内容审核', automatable: false }
      ],
      enabledTasks: ['fill-form', 'format-text', 'generate-section']
    }
  }
};

// 按类别分组
export const aiConfigsByCategory = {
  prepare: {
    id: 'prepare',
    name: '投标准备',
    items: ['ai-analysis', 'score-coverage', 'competition-intel', 'roi-analysis', 'market-timing']
  },
  composition: {
    id: 'composition',
    name: '标书编制',
    items: ['smart-assembly', 'solution-reuse', 'compliance-check']
  },
  collaboration: {
    id: 'collaboration',
    name: '团队协作',
    items: ['version-control', 'collaboration-center', 'auto-tasks']
  }
};

// 获取启用配置
export const getEnabledConfigs = () => {
  return Object.values(aiConfigs).filter(config => config.enabled);
};

// 根据 ID 获取配置
export const getConfigById = (id) => {
  return aiConfigs[id] || null;
};

// 根据类别获取配置
export const getConfigsByCategory = (category) => {
  return Object.values(aiConfigs).filter(config => config.category === category);
};
