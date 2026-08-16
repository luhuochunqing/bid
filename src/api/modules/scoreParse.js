// Input: httpClient and score-parse backend endpoints (spec 041, PR !2293)
// Output: scoreParseApi - items/scoring/status/results accessors
// Pos: src/api/modules/ - Frontend API module layer
// 一旦我被更新，务必更新我的开头注释，以及所属的文件夹的 md。

import httpClient from '../client.js'

/**
 * AI 评分标准解析（spec 041 后端真接口）。
 * 后端 ScoreParseController：/api/projects/{projectId}/score-parse/*
 */
export const scoreParseApi = {
  /** 阶段 1 评分项清单（含 estScore/estBasis/kbHit 与 summary 统计） */
  async getItems(projectId) {
    return httpClient.get(`/api/projects/${projectId}/score-parse/items`)
  },

  /** 触发评分标准解析（异步任务，返回 { taskId, status }；FR-021 覆盖旧解析结果） */
  async triggerParse(projectId) {
    return httpClient.post(`/api/projects/${projectId}/score-parse/parse`)
  },

  /** 解析进度轮询（PENDING/PROCESSING/COMPLETED/FAILED） */
  async getParseStatus(projectId) {
    return httpClient.get(`/api/projects/${projectId}/score-parse/parse/status`)
  },

  /** 触发阶段 2 实际打分（异步任务，返回 { taskId, status }） */
  async triggerScoring(projectId) {
    return httpClient.post(`/api/projects/${projectId}/score-parse/scoring`)
  },

  /** 阶段 2 打分进度轮询（PENDING/PROCESSING/COMPLETED/FAILED） */
  async getScoringStatus(projectId) {
    return httpClient.get(`/api/projects/${projectId}/score-parse/scoring/status`)
  },

  /** 阶段 2 打分结果（actualScore/evidence/quote/missedReason/suggestion） */
  async getResults(projectId) {
    return httpClient.get(`/api/projects/${projectId}/score-parse/results`)
  },
}
