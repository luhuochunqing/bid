<template>
  <div class="document-editor-page">
    <EditorHeader
      :project-name="projectInfo.name"
      :template-name="documentInfo.templateName"
      :can-export="canUseEditorExportActions"
      :can-archive="canUseEditorArchiveActions"
      @back="handleGoBack"
      @preview="handlePreview"
      @export="handleExport"
      @archive="handleArchive"
      @save="handleSave"
    />

    <div class="editor-container">
      <SectionTreePanel
        ref="sectionTreePanelRef"
        v-model:show-dialog="showSectionDialog"
        :section-tree-data="sectionTreeData"
        :dialog-title="sectionDialogTitle"
        v-model:section-form="sectionForm"
        :get-section-icon="getSectionIcon"
        :check-allow-drag="checkAllowDrag"
        :check-allow-drop="checkAllowDrop"
        @add-section="handleAddSection"
        @node-click="handleNodeClick"
        @node-drop="handleNodeDrop"
        @node-command="handleNodeCommand"
        @confirm-section="handleConfirmSection"
      />

      <EditorCenterPane
        v-model:current-section="currentSection"
        :zoom-level="zoomLevel"
        :base-font-size="baseFontSize"
        :sources="currentSectionSources"
        :knowledge-matches="knowledgeMatches"
        @zoom-in="handleZoomIn"
        @zoom-out="handleZoomOut"
        @content-change="handleContentChange"
        @insert-knowledge="handleInsertKnowledge"
      />

      <AssemblyPanel
        v-model:show-assembly-progress="showAssemblyProgress"
        :assembly-templates="assemblyTemplates"
        :assembly-history="assemblyHistory"
        v-model:assembly-form="assemblyForm"
        :assembly-steps="assemblySteps"
        :current-step-index="currentStepIndex"
        :is-assembling="isAssembling"
        :export-history="exportHistory"
        :archive-history="archiveHistory"
        @start-assembly="handleStartAssembly"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import EditorHeader from './editor/components/EditorHeader.vue'
import SectionTreePanel from './editor/components/SectionTreePanel.vue'
import EditorCenterPane from './editor/components/EditorCenterPane.vue'
import AssemblyPanel from './editor/components/AssemblyPanel.vue'
import { parseSectionMetadata } from './documentEditorHelpers.js'
import { useDocumentAssembly } from './useDocumentAssembly.js'
import { useDocumentKnowledge } from './useDocumentKnowledge.js'
import { useDocumentSidebar } from './useDocumentSidebar.js'
import { useDocumentExport } from './editor/composables/useDocumentExport.js'
import { getDefaultSectionData, getDefaultProjectInfo, getDefaultDocumentInfo } from './editor/defaultDocumentData.js'

const router = useRouter()
const route = useRoute()

const isRemoteProjectId = computed(() => /^\d+$/.test(String(route.params.id || '')))
const canUseLocalEditorActions = computed(() => false)
const canUseEditorExportActions = computed(() => isRemoteProjectId.value || canUseLocalEditorActions.value)
const canUseEditorArchiveActions = computed(() => isRemoteProjectId.value || canUseLocalEditorActions.value)

const projectInfo = ref(getDefaultProjectInfo())
const documentInfo = ref(getDefaultDocumentInfo())
const sectionData = ref(getDefaultSectionData())

const currentSection = ref(null)
const currentStructureId = ref(null)
const activeSectionId = ref(null)
const sectionTreePanelRef = ref(null)
const sectionTreeRef = computed(() => sectionTreePanelRef.value?.sectionTreeRef || null)
const zoomLevel = ref(100)
const baseFontSize = 14

const handleContentChange = () => {}

const {
  knowledgeMatches,
  loadKnowledgeMatches,
  handleInsertKnowledge
} = useDocumentKnowledge({
  currentSection,
  projectInfo,
  documentInfo,
  isRemoteProjectId
})

const sidebar = useDocumentSidebar({
  route,
  router,
  projectInfo,
  documentInfo,
  sectionData,
  currentSection,
  currentStructureId,
  activeSectionId,
  sectionTreeRef,
  isRemoteProjectId,
  onSectionSelected: (section) => loadKnowledgeMatches(section)
})

const {
  sectionTreeData,
  showSectionDialog,
  sectionDialogTitle,
  sectionForm,
  loadEditorData,
  handleGoBack,
  handleNodeClick,
  handleNodeDrop,
  handleAddSection,
  handleNodeCommand,
  handleConfirmSection,
  handleSave,
  getSectionIcon,
  checkAllowDrag,
  checkAllowDrop,
  selectSectionById
} = sidebar

const {
  assemblyTemplates,
  assemblyHistory,
  assemblyForm,
  assemblySteps,
  currentStepIndex,
  isAssembling,
  showAssemblyProgress,
  loadAssemblyTemplates,
  loadAssemblyHistory,
  handleStartAssembly
} = useDocumentAssembly({
  sectionData,
  currentSection,
  projectInfo,
  documentInfo,
  isRemoteProjectId,
  onSectionSelected: (section) => selectSectionById(section.id)
})

const {
  exportHistory,
  archiveHistory,
  loadExportArtifacts,
  handlePreview,
  handleExport,
  handleArchive
} = useDocumentExport({
  route,
  projectInfo,
  documentInfo,
  sectionData,
  isRemoteProjectId
})

// --- useDocumentPerformanceInsert (内联：唯一引用 + ≤80行) ---
function checkPendingInsert() {
  try {
    const raw = sessionStorage.getItem('pendingPerformanceInsert')
    if (!raw) return
    const data = JSON.parse(raw)
    if (!data.timestamp || Date.now() - data.timestamp > 5 * 60 * 1000) {
      sessionStorage.removeItem('pendingPerformanceInsert')
      return
    }
    const sections = sectionData.value?.sections || []
    let target = null
    function find(list) {
      for (const s of list || []) {
        if (s.type === 'section' && (/案例|业绩|experience|performance/i.test(s.name || ''))) {
          return s
        }
        if (s.children) {
          const found = find(s.children)
          if (found) return found
        }
      }
      return null
    }
    target = find(sections)
    if (target) {
      currentSection.value = target
      const content_ = `\n\n## 业绩信息\n\n- 合同名称：${data.contractName || ''}\n- 签约单位：${data.signingEntity || ''}\n- 剩余有效期：${data.remainingDays != null ? data.remainingDays + ' 天' : ''}\n${data.reason ? '- 备注：' + data.reason : ''}\n\n${data.fullText || ''}\n`
      target.content = (target.content || '') + content_
      ElMessage.success('业绩信息已自动插入当前章节')
    } else {
      ElMessage.info('未找到案例/业绩章节，请手动选择章节后粘贴')
    }
    sessionStorage.removeItem('pendingPerformanceInsert')
  } catch {
    sessionStorage.removeItem('pendingPerformanceInsert')
  }
}

const currentSectionSources = computed(
  () => parseSectionMetadata(currentSection.value?.metadata).sources || []
)

function handleZoomIn() {
  if (zoomLevel.value < 150) {
    zoomLevel.value += 10
  }
}

function handleZoomOut() {
  if (zoomLevel.value > 70) {
    zoomLevel.value -= 10
  }
}

async function loadDocumentData() {
  await loadEditorData()
  await loadKnowledgeMatches(currentSection.value)
  await loadAssemblyTemplates()
  await loadAssemblyHistory()
  await loadExportArtifacts(route.params.id)
}

onMounted(() => {
  loadDocumentData().then(() => {
    setTimeout(checkPendingInsert, 800)
  })
})

watch(() => route.params.id, () => {
  loadDocumentData()
})
</script>

<style scoped>
.document-editor-page {
  height: 100vh;
  display: flex;
  flex-direction: column;
  background-color: var(--bg-subtle);
}

.editor-container {
  display: flex;
  flex: 1;
  overflow: hidden;
  gap: 16px;
  padding: 16px;
}

@media (max-width: 1200px) {
  .editor-container {
    flex-direction: column;
    overflow-y: auto;
  }
}
</style>
