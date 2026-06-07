import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { collaborationApi, projectsApi } from '@/api'

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

function findFirstEditableSection(sections) {
  for (const item of sections || []) {
    if (item.type === 'section' || !Array.isArray(item.children) || item.children.length === 0) {
      return item
    }
    const nested = findFirstEditableSection(item.children)
    if (nested) return nested
  }
  return sections?.[0] || null
}

function buildSectionOrders(sections, orders = {}) {
  (sections || []).forEach((item, index) => {
    const apiId = resolveSectionApiId(item)
    if (apiId && /^\d+$/.test(String(apiId))) {
      orders[String(apiId)] = index + 1
    }
    if (Array.isArray(item.children) && item.children.length > 0) {
      buildSectionOrders(item.children, orders)
    }
  })
  return orders
}

function resolveSectionApiId(section) {
  return section?.apiId || section?.id
}

export function useDocumentSidebar({
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
  onSectionSelected
}) {
  const sectionTreeData = computed(() => sectionData.value.sections)
  const showSectionDialog = ref(false)
  const sectionDialogTitle = ref('添加章节')
  const sectionForm = ref({
    id: '',
    name: '',
    type: 'section',
    parentId: ''
  })
  const editingSectionId = ref('')

  async function loadProjectInfo(projectId) {
    try {
      const result = await projectsApi.getDetail(projectId)
      if (result?.success && result?.data) {
        projectInfo.value = {
          id: result.data.id,
          name: result.data.name || projectInfo.value.name
        }
      }
    } catch {
      // 静默兜底：项目详情拉取失败不阻塞侧边栏渲染。
    }
  }

  async function ensureEditorStructure(projectId) {
    if (!isRemoteProjectId.value) return null

    try {
      const result = await collaborationApi.editor.getStructure(projectId)
      if (result?.success && result?.data?.id) {
        return result.data
      }
    } catch (error) {
      if (error?.response?.status && error.response.status !== 404) {
        throw error
      }
    }

    const created = await collaborationApi.editor.createStructure(projectId, {
      name: `${projectInfo.value.name || '项目'} 文档结构`
    })
    return created?.data || null
  }

  function syncCurrentSectionReference() {
    if (!activeSectionId.value) return
    const latest = findSectionById(sectionData.value.sections, activeSectionId.value)
    if (latest) {
      currentSection.value = latest
    }
  }

  function selectSectionById(id) {
    const section = findSectionById(sectionData.value.sections, id)
    if (!section) return null

    currentSection.value = section
    activeSectionId.value = section.id
    if (typeof onSectionSelected === 'function') {
      onSectionSelected(section)
    }
    return section
  }

  async function loadEditorData() {
    const projectId = route.params.id
    await loadProjectInfo(projectId)

    if (!isRemoteProjectId.value) {
      const fallbackSection = findFirstEditableSection(sectionData.value.sections)
      if (fallbackSection) {
        selectSectionById(fallbackSection.id)
      }
      return
    }

    try {
      const structure = await ensureEditorStructure(projectId)
      currentStructureId.value = structure?.id || null
      if (structure?.name) {
        documentInfo.value.templateName = structure.name
      }

      const treeResult = await collaborationApi.editor.getEditorTree(projectId)
      sectionData.value.sections = Array.isArray(treeResult?.data) ? treeResult.data : []

      const preferredSection = activeSectionId.value
        ? findSectionById(sectionData.value.sections, activeSectionId.value)
        : findFirstEditableSection(sectionData.value.sections)

      if (preferredSection) {
        selectSectionById(preferredSection.id)
      } else {
        currentSection.value = null
        activeSectionId.value = null
      }
    } catch (error) {
      ElMessage.warning('文档结构加载失败，请检查网络连接后重试')
      sectionData.value.sections = []
      currentStructureId.value = null
      currentSection.value = null
      activeSectionId.value = null
    }
  }

  function getSectionIcon(type) {
    return type === 'folder' ? '📁' : '📄'
  }

  function checkAllowDrag() {
    return true
  }

  function checkAllowDrop(draggingNode, dropNode, type) {
    if (isRemoteProjectId.value) {
      if (type === 'inner') return false
      return String(draggingNode.data.parentId || '') === String(dropNode.data.parentId || '')
    }
    if (type === 'inner') {
      return dropNode.data.type === 'folder'
    }
    return true
  }

  function handleNodeClick(data) {
    selectSectionById(data.id)
  }

  async function handleNodeDrop() {
    if (!isRemoteProjectId.value || !currentStructureId.value) {
      ElMessage.success('章节顺序已更新')
      return
    }

    try {
      await collaborationApi.editor.reorderSections(route.params.id, {
        structureId: currentStructureId.value,
        sectionOrders: buildSectionOrders(sectionData.value.sections)
      })
      syncCurrentSectionReference()
      ElMessage.success('章节顺序已同步')
    } catch (error) {
      ElMessage.error(`章节排序同步失败: ${error.message}`)
      await loadEditorData()
    }
  }

  function handleAddSection() {
    sectionDialogTitle.value = '添加章节'
    sectionForm.value = {
      id: '',
      name: '',
      type: 'section',
      parentId: ''
    }
    editingSectionId.value = ''
    showSectionDialog.value = true
  }

  function handleNodeCommand(command, data) {
    switch (command) {
      case 'add':
        sectionDialogTitle.value = '添加子章节'
        sectionForm.value = {
          id: '',
          name: '',
          type: 'section',
          parentId: data.id
        }
        editingSectionId.value = ''
        showSectionDialog.value = true
        break
      case 'rename':
        sectionDialogTitle.value = '重命名章节'
        sectionForm.value = {
          id: data.id,
          name: data.name,
          type: data.type,
          parentId: ''
        }
        editingSectionId.value = data.id
        showSectionDialog.value = true
        break
      case 'delete':
        handleDeleteSection(data)
        break
    }
  }

  function deleteSectionById(id) {
    const deleteInArray = (arr) => {
      const index = arr.findIndex((item) => item.id === id)
      if (index > -1) {
        arr.splice(index, 1)
        return true
      }
      for (const item of arr) {
        if (Array.isArray(item.children) && deleteInArray(item.children)) {
          return true
        }
      }
      return false
    }

    deleteInArray(sectionData.value.sections)
  }

  function handleDeleteSection(data) {
    ElMessageBox.confirm('确定要删除该章节吗？', '确认删除', {
      type: 'warning'
    }).then(() => {
      if (isRemoteProjectId.value && /^\d+$/.test(String(resolveSectionApiId(data)))) {
        collaborationApi.editor.deleteSection(route.params.id, resolveSectionApiId(data))
          .then(() => {
            deleteSectionById(data.id)
            ElMessage.success('章节已删除')
            if (currentSection.value?.id === data.id) {
              currentSection.value = findFirstEditableSection(sectionData.value.sections)
              activeSectionId.value = currentSection.value?.id || null
              if (currentSection.value && typeof onSectionSelected === 'function') {
                onSectionSelected(currentSection.value)
              }
            }
          })
          .catch((error) => {
            ElMessage.error(`删除章节失败: ${error.message}`)
          })
        return
      }

      deleteSectionById(data.id)
      ElMessage.success('章节已删除')
      if (currentSection.value?.id === data.id) {
        currentSection.value = null
      }
    }).catch(() => {})
  }

  function handleConfirmSection() {
    if (!sectionForm.value.name) {
      ElMessage.warning('请输入章节名称')
      return
    }

    if (editingSectionId.value && isRemoteProjectId.value) {
      const targetSection = findSectionById(sectionData.value.sections, editingSectionId.value)
      collaborationApi.editor.updateSection(route.params.id, resolveSectionApiId(targetSection), {
        title: sectionForm.value.name
      }).then((result) => {
        if (targetSection) {
          targetSection.name = result?.data?.name || sectionForm.value.name
        }
        syncCurrentSectionReference()
        ElMessage.success('章节已重命名')
        showSectionDialog.value = false
      }).catch((error) => {
        ElMessage.error(`重命名失败: ${error.message}`)
      })
      return
    }

    if (editingSectionId.value) {
      const section = findSectionById(sectionData.value.sections, editingSectionId.value)
      if (section) {
        section.name = sectionForm.value.name
        ElMessage.success('章节已重命名')
      }
    } else if (isRemoteProjectId.value && currentStructureId.value) {
      const parent = sectionForm.value.parentId ? findSectionById(sectionData.value.sections, sectionForm.value.parentId) : null
      collaborationApi.editor.createSection(route.params.id, {
        structureId: currentStructureId.value,
        parentId: parent ? resolveSectionApiId(parent) : null,
        sectionType: parent ? 'SECTION' : 'CHAPTER',
        title: sectionForm.value.name,
        content: sectionForm.value.type === 'section'
          ? `## ${sectionForm.value.name}\n\n在此处添加内容...`
          : '',
        orderIndex: parent?.children?.length ? parent.children.length + 1 : sectionData.value.sections.length + 1
      }).then((result) => {
        const newSection = result?.data
        if (!newSection) return

        if (sectionForm.value.parentId && parent) {
          if (!Array.isArray(parent.children)) parent.children = []
          parent.children.push(newSection)
        } else {
          sectionData.value.sections.push(newSection)
        }
        selectSectionById(newSection.id)
        ElMessage.success('章节已添加')
        showSectionDialog.value = false
      }).catch((error) => {
        ElMessage.error(`添加章节失败: ${error.message}`)
      })
      return
    } else {
      const newSection = {
        id: Date.now().toString(),
        name: sectionForm.value.name,
        type: sectionForm.value.type,
        content: sectionForm.value.type === 'section'
          ? `## ${sectionForm.value.name}\n\n在此处添加内容...`
          : ''
      }

      if (sectionForm.value.parentId) {
        const parent = findSectionById(sectionData.value.sections, sectionForm.value.parentId)
        if (parent) {
          if (!Array.isArray(parent.children)) {
            parent.children = []
          }
          parent.children.push(newSection)
        }
      } else {
        sectionData.value.sections.push(newSection)
      }

      ElMessage.success('章节已添加')
    }

    showSectionDialog.value = false
  }

  function handleSave() {
    if (!currentSection.value) {
      ElMessage.warning('请先选择要保存的章节')
      return
    }

    if (!isRemoteProjectId.value || !/^\d+$/.test(String(resolveSectionApiId(currentSection.value)))) {
      ElMessage.success('保存成功')
      return
    }

    collaborationApi.editor.updateSection(route.params.id, resolveSectionApiId(currentSection.value), {
      title: currentSection.value.name,
      content: currentSection.value.content,
      metadata: currentSection.value.metadata || '',
      orderIndex: currentSection.value.orderIndex ?? 0
    }).then((result) => {
      currentSection.value = {
        ...currentSection.value,
        ...(result?.data || {})
      }
      activeSectionId.value = currentSection.value.id
      syncCurrentSectionReference()
      ElMessage.success('保存成功')
    }).catch((error) => {
      ElMessage.error(`保存失败: ${error.message}`)
    })
  }

  function handleGoBack() {
    router.back()
  }

  return {
    sectionTreeData,
    sectionTreeRef,
    showSectionDialog,
    sectionDialogTitle,
    sectionForm,
    editingSectionId,
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
    findSectionById,
    resolveSectionApiId,
    selectSectionById,
    syncCurrentSectionReference
  }
}
