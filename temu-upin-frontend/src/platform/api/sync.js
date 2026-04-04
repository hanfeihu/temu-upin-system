import apiClient from '@/api'

export const syncApi = {
  // ==================== 同步配置 ====================
  getConfigs(shopId) {
    return apiClient.get('/sync/config', { params: { shopId } })
  },
  saveConfigs(payload) {
    return apiClient.post('/sync/config', payload)
  },

  // ==================== 同步任务 ====================
  getSyncLogs(params = {}) {
    return apiClient.get('/sync/tasks/logs', { params })
  },
  triggerSync(payload) {
    return apiClient.post('/sync/tasks/trigger', payload)
  },

  // ==================== 任务管理（新） ====================
  createSyncTasks(payload) {
    return apiClient.post('/sync/tasks', payload)
  },
  listSyncTasks(params = {}) {
    return apiClient.get('/sync/tasks', { params })
  },
  getTaskDetail(taskId) {
    return apiClient.get(`/sync/tasks/${taskId}`)
  },
  getTaskProgress(taskId) {
    return apiClient.get(`/sync/tasks/${taskId}/progress`)
  },
  retryTask(taskId, mode = 'CONTINUE') {
    return apiClient.post(`/sync/tasks/${taskId}/retry`, { mode })
  },
  cancelTask(taskId) {
    return apiClient.post(`/sync/tasks/${taskId}/cancel`)
  },
  clearShopSyncData(shopId) {
    return apiClient.delete('/sync/tasks/shop-data', { params: { shopId } })
  },

  // ==================== 商品数据 ====================
  getGoodsList(params = {}) {
    return apiClient.get('/sync/goods', { params })
  },
  getGoodsDetail(id) {
    return apiClient.get(`/sync/goods/${id}`)
  },
  repairGoodsDetails(shopId) {
    return apiClient.post('/sync/goods/repair-details', null, { params: { shopId } })
  },
  getRepairGoodsDetailsStatus(jobId) {
    return apiClient.get('/sync/goods/repair-details/status', { params: { jobId } })
  },
  getLatestRepairGoodsDetailsStatus(shopId) {
    return apiClient.get('/sync/goods/repair-details/status/latest', { params: { shopId } })
  },

  // ==================== 核价单 ====================
  getPriceReviewList(params = {}) {
    return apiClient.get('/sync/price-review', { params })
  },
  getPriceReviewDetail(id) {
    return apiClient.get(`/sync/price-review/${id}`)
  },
  batchReviewPrice(payload) {
    return apiClient.post('/sync/price-review/batch-review', payload)
  },

  // ==================== 调价单 ====================
  getPriceAdjustList(params = {}) {
    return apiClient.get('/sync/price-adjust', { params })
  },
  getPriceAdjustDetail(id) {
    return apiClient.get(`/sync/price-adjust/${id}`)
  },
  clearPriceAdjustLocalData(shopId) {
    return apiClient.delete('/sync/price-adjust/local-data', { params: { shopId } })
  },
  batchReviewAdjust(payload) {
    return apiClient.post('/sync/price-adjust/batch-review', payload)
  },

  // ==================== 活动 ====================
  getActivityList(params = {}) {
    return apiClient.get('/sync/activity/list', { params })
  },
  getActivityDetail(params = {}) {
    return apiClient.get('/sync/activity/detail', { params })
  },
  matchActivityProducts(payload) {
    return apiClient.post('/sync/activity/match-products', payload)
  },
  getActivitySessions(params = {}) {
    return apiClient.get('/sync/activity/sessions', { params })
  },
  queryActivitySessions(payload) {
    return apiClient.post('/sync/activity/sessions/query', payload)
  },
  getEnrollmentList(params = {}) {
    return apiClient.get('/sync/activity/enrollments', { params })
  },
  getEnrollmentDetail(id) {
    return apiClient.get(`/sync/activity/enrollments/${id}`)
  },
  batchEnroll(payload) {
    return apiClient.post('/sync/activity/batch-enroll', payload)
  }
}
