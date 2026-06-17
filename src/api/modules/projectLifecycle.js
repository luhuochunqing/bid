// Input: httpClient and project list/lifecycle request payloads
// Output: projectLifecycleApi - thin wrappers for /api/projects/{id}/{initiation,drafting,evaluation,result,retrospective,closure,stage} and list/export endpoints
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

const base = (id) => `/api/projects/${id}`

export const projectLifecycleApi = {
  // WS-G stage snapshot
  getStage(id) {
    return httpClient.get(`${base(id)}/stage`)
  },

  // Project list & export
  getList(params = {}) {
    return httpClient.get('/api/projects', { params })
  },
  exportList(params = {}) {
    return httpClient.get('/api/projects/export', { params, responseType: 'blob' })
  },

  // WS-A initiation
  getInitiation(id) {
    return httpClient.get(`${base(id)}/initiation`, { skipGlobalErrorMessage: true })
  },
  submitInitiation(id, payload) {
    return httpClient.post(`${base(id)}/initiation`, payload)
  },
  updateInitiation(id, payload) {
    return httpClient.patch(`${base(id)}/initiation`, payload)
  },
  approveInitiation(id, payload) {
    return httpClient.post(`${base(id)}/initiation/approve`, payload)
  },
  rejectInitiation(id, payload) {
    return httpClient.post(`${base(id)}/initiation/reject`, payload)
  },

  // WS-B drafting
  getDrafting(id) {
    return httpClient.get(`${base(id)}/drafting`)
  },
  assignDraftingLeads(id, payload) {
    return httpClient.patch(`${base(id)}/drafting/leads`, payload)
  },
  advanceDrafting(id, payload = {}) {
    return httpClient.post(`${base(id)}/drafting/advance`, payload)
  },
  submitBid(id) {
    return httpClient.post(`${base(id)}/drafting/submit-bid`)
  },
  submitBidForReview(id, payload = {}) {
    return httpClient.post(`${base(id)}/drafting/submit-review`, payload)
  },
  approveBid(id, payload) {
    return httpClient.post(`${base(id)}/drafting/approve`, payload)
  },
  rejectBid(id, payload) {
    return httpClient.post(`${base(id)}/drafting/reject`, payload)
  },

  // WS-C evaluation
  getEvaluation(id) {
    return httpClient.get(`${base(id)}/evaluation`, { skipGlobalErrorMessage: true })
  },
  transitionEvaluationSubStage(id, payload) {
    return httpClient.patch(`${base(id)}/evaluation/sub-stage`, payload)
  },
  advanceEvaluation(id) {
    return httpClient.post(`${base(id)}/evaluation/advance`)
  },
  attachEvaluationEvidence(id, payload) {
    return httpClient.post(`${base(id)}/evaluation/evidence`, payload)
  },
  updateEvaluationForm(id, payload) {
    return httpClient.patch(`${base(id)}/evaluation/form`, payload)
  },
  abandonBid(id, payload) {
    return httpClient.post(`${base(id)}/evaluation/abandon`, payload)
  },
  submitToBid(id) {
    return httpClient.post(`${base(id)}/submit-to-bid-document`)
  },

  // WS-D result
  getResult(id) {
    return httpClient.get(`${base(id)}/result`)
  },
  registerResult(id, payload) {
    return httpClient.post(`${base(id)}/result`, payload)
  },

  // WS-E retrospective
  getRetrospective(id) {
    return httpClient.get(`${base(id)}/retrospective`)
  },
  submitRetrospective(id, payload) {
    return httpClient.post(`${base(id)}/retrospective`, payload)
  },

  // WS-F closure
  getClosurePreview(id) {
    return httpClient.get(`${base(id)}/closure/preview`)
  },
  submitClosure(id, payload) {
    return httpClient.post(`${base(id)}/closure`, payload)
  },
  approveClosure(id) {
    return httpClient.post(`${base(id)}/closure/approve`)
  },
  rejectClosure(id, payload) {
    return httpClient.post(`${base(id)}/closure/reject`, payload)
  },

  // WS-F project archive export
  exportProjectArchive(id) {
    return httpClient.get(`/api/archive/export-zip/${id}`, { responseType: 'blob' })
  },

  // WS-F rebid (二次招标)
  rebidProject(id) {
    return httpClient.post(`${base(id)}/rebid`)
  },
}

export default projectLifecycleApi
