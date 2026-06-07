import { computed, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { documentAssemblyApi } from '@/api/modules/collaboration.js'
import { useUserStore } from '@/stores/user'
import {
  applyAssemblyContentToSections,
  buildAssemblyVariables
} from './documentEditorHelpers.js'

function formatDateTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return date.toLocaleString('zh-CN', { hour12: false })
}

function findSectionById(sections, id) {
  for (const section of sections || []) {
    if (String(section.id) === String(id)) {
      return section
    }
    if (Array.isArray(section.children) && section.children.length > 0) {
      const found = findSectionById(section.children, id)
      if (found) return found
    }
  }
  return null
}

function flattenSections(sections, result = []) {
  for (const section of sections || []) {
    result.push(section)
    if (Array.isArray(section.children) && section.children.length > 0) {
      flattenSections(section.children, result)
    }
  }
  return result
}

function getSectionGroupPatterns(groupKey) {
  const map = {
    technical: [/技术方案/, /技术/, /^1\./],
    cases: [/案例/, /成功案例/, /^3\./],
    qualification: [/资质/, /证书/, /^2\.1/],
    service: [/服务承诺/, /售后/, /^2\.2/],
    delivery: [/交付/, /实施计划/, /^2\.3/]
  }

  return map[groupKey] || []
}

function isGroupSection(section, groupKey) {
  const patterns = getSectionGroupPatterns(groupKey)
  if (patterns.length === 0) return false

  const text = `${section.name || ''} ${section.id || ''}`
  return patterns.some((pattern) => pattern.test(text))
}

export function useDocumentAssembly({
  sectionData,
  currentSection: _currentSection,
  projectInfo,
  documentInfo,
  isRemoteProjectId,
  onSectionSelected
}) {
  const userStore = useUserStore()
  const assemblyTemplates = ref([])
  const assemblyHistory = ref([])
  const isAssembling = ref(false)
  const showAssemblyProgress = ref(false)
  const currentStepIndex = ref(0)
  const assemblySteps = ref([
    '分析评分标准...',
    '匹配技术方案模板...',
    '检索相关案例...',
    '组装资质文件...',
    '生成服务承诺...',
    '整合交付计划...',
    '合规性检查...'
  ])
  const assemblyForm = reactive({
    templateId: '',
    sections: []
  })

  const selectedAssemblyTemplate = computed(() =>
    assemblyTemplates.value.find((item) => String(item.id) === String(assemblyForm.templateId)) || null)

  async function loadAssemblyTemplates() {
    if (!isRemoteProjectId.value) {
      assemblyTemplates.value = []
      return []
    }

    try {
      const response = await documentAssemblyApi.getTemplates()
      assemblyTemplates.value = Array.isArray(response?.data) ? response.data : []
      if (!assemblyForm.templateId && assemblyTemplates.value[0]) {
        assemblyForm.templateId = String(assemblyTemplates.value[0].id)
      }
      return assemblyTemplates.value
    } catch (error) {
      assemblyTemplates.value = []
      return []
    }
  }

  async function loadAssemblyHistory() {
    if (!isRemoteProjectId.value) {
      assemblyHistory.value = []
      return []
    }

    try {
      const response = await documentAssemblyApi.getAssemblies(projectInfo.value.id)
      const templateMap = new Map(assemblyTemplates.value.map((template) => [String(template.id), template.name]))
      assemblyHistory.value = Array.isArray(response?.data)
        ? response.data.map((item) => ({
          id: item.id,
          templateId: item.templateId,
          templateName: templateMap.get(String(item.templateId)) || `模板 #${item.templateId}`,
          time: formatDateTime(item.assembledAt || item.createdAt),
          assembledContent: item.assembledContent || '',
          variables: item.variables || ''
        }))
        : []
      return assemblyHistory.value
    } catch (error) {
      assemblyHistory.value = []
      return []
    }
  }

  function getTemplateName(templateId) {
    return selectedAssemblyTemplate.value?.name
      || assemblyTemplates.value.find((item) => String(item.id) === String(templateId))?.name
      || '文档装配模板'
  }

  function buildSelectedSections() {
    const flatSections = flattenSections(sectionData.value.sections)
    return assemblyForm.sections.flatMap((groupKey) =>
      flatSections.filter((section) => !Array.isArray(section.children) || section.children.length === 0)
        .filter((section) => isGroupSection(section, groupKey)))
  }

  function startAssemblyProcess() {
    isAssembling.value = true
    showAssemblyProgress.value = true
    currentStepIndex.value = 0

    const steps = assemblySteps.value
    let stepIndex = 0

    const executeNextStep = () => {
      if (stepIndex < steps.length) {
        currentStepIndex.value = stepIndex
        setTimeout(() => {
          stepIndex += 1
          executeNextStep()
        }, 600)
        return
      }

      completeAssembly()
    }

    executeNextStep()
  }

  async function completeAssembly() {
    try {
      const template = selectedAssemblyTemplate.value
      const selectedSections = buildSelectedSections()
      const variables = buildAssemblyVariables({
        projectInfo: projectInfo.value,
        documentInfo: documentInfo.value,
        template,
        selectedSections
      })

      const response = await documentAssemblyApi.assembleDocument(projectInfo.value.id, {
        templateId: template?.id,
        variables,
        assembledBy: userStore.currentUser?.id ?? null
      })

      if (!response?.success || !response?.data) {
        throw new Error(response?.msg || '装配失败')
      }

      const assembledContent = response.data.assembledContent || ''
      const filledIds = applyAssemblyContentToSections(
        sectionData.value.sections,
        selectedSections.map((section) => section.id),
        assembledContent,
        template?.name || '文档装配'
      )

      const firstSection = filledIds.length > 0
        ? findSectionById(sectionData.value.sections, filledIds[0])
        : null

      if (firstSection && typeof onSectionSelected === 'function') {
        onSectionSelected(firstSection)
      }

      assemblyHistory.value.unshift({
        id: response.data.id ?? Date.now(),
        templateId: template?.id,
        templateName: template?.name || '文档装配模板',
        time: formatDateTime(response.data.assembledAt || new Date().toISOString()),
        assembledContent,
        variables
      })

      ElMessage.success(`智能装配完成！已填充 ${filledIds.length} 个章节`)
    } catch (error) {
      ElMessage.error(`装配失败: ${error.message || '未知错误'}`)
    } finally {
      showAssemblyProgress.value = false
      isAssembling.value = false
    }
  }

  function handleStartAssembly() {
    if (assemblyForm.sections.length === 0) {
      ElMessage.warning('请至少选择一个章节')
      return
    }
    if (!selectedAssemblyTemplate.value) {
      ElMessage.warning('请先选择一个模板')
      return
    }

    ElMessageBox.confirm(
      `确定要使用"${getTemplateName(assemblyForm.templateId)}"模板生成${assemblyForm.sections.length}个章节的内容吗？`,
      '确认装配',
      {
        confirmButtonText: '开始装配',
        cancelButtonText: '取消',
        type: 'warning'
      }
    ).then(() => {
      startAssemblyProcess()
    }).catch(() => {})
  }

  return {
    assemblyTemplates,
    assemblyHistory,
    assemblyForm,
    assemblySteps,
    currentStepIndex,
    isAssembling,
    showAssemblyProgress,
    selectedAssemblyTemplate,
    loadAssemblyTemplates,
    loadAssemblyHistory,
    getTemplateName,
    handleStartAssembly
  }
}
