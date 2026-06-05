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

const router = useRouter()
const route = useRoute()

const isRemoteProjectId = computed(() => /^\d+$/.test(String(route.params.id || '')))
const canUseLocalEditorActions = computed(() => false)
const canUseEditorExportActions = computed(() => isRemoteProjectId.value || canUseLocalEditorActions.value)
const canUseEditorArchiveActions = computed(() => isRemoteProjectId.value || canUseLocalEditorActions.value)

const projectInfo = ref({
  id: 'P001',
  name: '智慧城市IOC项目'
})

const documentInfo = ref({
  templateId: 'TPL_SMARTCITY',
  templateName: '智慧城市标书模板'
})

const sectionData = ref({
  sections: [
    {
      id: 'cover',
      name: '封面',
      type: 'section',
      content: '# 智慧城市IOC项目\n\n投标文件\n\n投标单位：西域科技股份有限公司\n投标日期：2025年2月'
    },
    {
      id: '1',
      name: '技术方案',
      type: 'folder',
      children: [
        {
          id: '1.1',
          name: '项目背景',
          type: 'section',
          content: '## 项目背景\n\n在此处编辑项目背景...'
        },
        {
          id: '1.2',
          name: '需求分析',
          type: 'section',
          content: '## 需求分析\n\n在此处编辑需求分析...'
        }
      ]
    },
    {
      id: '2',
      name: '商务文件',
      type: 'folder',
      children: [
        {
          id: '2.1',
          name: '投标函',
          type: 'section',
          content: '## 投标函\n\n在此处编辑投标函...'
        },
        {
          id: '2.2',
          name: '报价清单',
          type: 'section',
          content: '## 报价清单\n\n在此处编辑报价清单...'
        },
        {
          id: '2.3',
          name: '交付计划',
          type: 'section',
          content: '## 交付计划\n\n在此处编辑交付计划...'
        }
      ]
    },
    {
      id: '3',
      name: '案例展示',
      type: 'folder',
      children: [
        {
          id: '3.1',
          name: '智慧城市案例',
          type: 'section',
          content: '## 案例展示\n\n在此处编辑案例展示...'
        }
      ]
    }
  ]
})

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
  loadDocumentData()
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
