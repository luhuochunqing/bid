import { computed, ref } from 'vue'
import { shallowMount } from '@vue/test-utils'
import { beforeEach, describe, expect, it, vi } from 'vitest'

const refs = {
  drawerVisible: ref(true),
  showWorkbench: ref(false),
  currentRun: ref(null),
  applyResult: ref(null),
  importResult: ref(null),
  tenderFile: ref(null),
  error: ref(''),
  importing: ref(false),
  creating: ref(false),
  fetching: ref(false),
  applying: ref(false),
  reviewing: ref(false),
}

const bidAgent = {
  ...refs,
  currentRunId: computed(() => refs.currentRun.value?.runId || null),
  selectedTenderFileName: computed(() => refs.tenderFile.value?.name || ''),
  projectId: computed(() => 12),
  selectTenderFile: vi.fn(),
  clearTenderFile: vi.fn(),
  importTenderDocument: vi.fn(),
  confirmWorkbench: vi.fn(),
  createRun: vi.fn(),
  fetchRun: vi.fn(),
  applyBidAgentResult: vi.fn(),
  createReview: vi.fn(),
  goToEditor: vi.fn(),
}

const context = {
  project: ref({ id: 12 }),
  bidAgent,
}

vi.mock('@/composables/projectDetail/context.js', () => ({
  useProjectDetailContext: () => context,
}))

import ProjectDetailBidAgentDrawer from './ProjectDetailBidAgentDrawer.vue'

function resetRefs(overrides = {}) {
  refs.drawerVisible.value = true
  refs.showWorkbench.value = false
  refs.currentRun.value = null
  refs.applyResult.value = null
  refs.importResult.value = null
  refs.tenderFile.value = null
  refs.error.value = ''
  refs.importing.value = false
  refs.creating.value = false
  refs.fetching.value = false
  refs.applying.value = false
  refs.reviewing.value = false

  Object.keys(overrides).forEach(key => {
    if (refs[key]) {
      refs[key].value = overrides[key]
    }
  })
}

function mountDrawer() {
  return shallowMount(ProjectDetailBidAgentDrawer, {
    global: {
      stubs: {
        'el-drawer': { props: ['modelValue'], template: '<div><slot /></div>' },
        'el-button': true,
        'el-tag': true,
        'el-alert': true,
        'el-empty': true,
        'el-dialog': true,
        'DocVerificationWorkbench': true,
        'ProjectDetailBidAgentTenderUpload': true,
        'QualificationMatchPanel': true,
        'TechnicalRequirementsPanel': true,
        'CommercialRequirementsPanel': true,
        'RiskRedLinePanel': true
      }
    },
  })
}

describe('ProjectDetailBidAgentDrawer', () => {
  beforeEach(() => {
    resetRefs()
    vi.clearAllMocks()
  })

  it('mounts without errors', () => {
    const wrapper = mountDrawer()
    expect(wrapper.exists()).toBe(true)
  })

  it('renders correctly when run is completed', () => {
    resetRefs({
      currentRun: { runId: 'run-1', status: 'COMPLETED' }
    })
    const wrapper = mountDrawer()
    expect(wrapper.exists()).toBe(true)
  })
})
