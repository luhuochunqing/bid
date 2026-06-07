export function getDefaultSectionData() {
  return {
    sections: [
      { id: 'cover', name: '封面', type: 'section', content: '# 智慧城市IOC项目\n\n投标文件\n\n投标单位：西域科技股份有限公司\n投标日期：2025年2月' },
      { id: '1', name: '技术方案', type: 'folder', children: [
        { id: '1.1', name: '项目背景', type: 'section', content: '## 项目背景\n\n在此处编辑项目背景...' },
        { id: '1.2', name: '需求分析', type: 'section', content: '## 需求分析\n\n在此处编辑需求分析...' }
      ]},
      { id: '2', name: '商务文件', type: 'folder', children: [
        { id: '2.1', name: '投标函', type: 'section', content: '## 投标函\n\n在此处编辑投标函...' },
        { id: '2.2', name: '报价清单', type: 'section', content: '## 报价清单\n\n在此处编辑报价清单...' },
        { id: '2.3', name: '交付计划', type: 'section', content: '## 交付计划\n\n在此处编辑交付计划...' }
      ]},
      { id: '3', name: '案例展示', type: 'folder', children: [
        { id: '3.1', name: '智慧城市案例', type: 'section', content: '## 案例展示\n\n在此处编辑案例展示...' }
      ]}
    ]
  }
}

export function getDefaultProjectInfo() {
  return { id: 'P001', name: '智慧城市IOC项目' }
}

export function getDefaultDocumentInfo() {
  return { templateId: 'TPL_SMARTCITY', templateName: '智慧城市标书模板' }
}
