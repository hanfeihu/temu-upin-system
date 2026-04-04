<template>
  <ProLayout title="数据同步">
    <div class="page">
      <!-- 店铺选择 -->
      <a-card :bordered="false">
        <ShopTabs v-model="shopId" :shops="shops" :loading="shopsLoading" empty-text="暂无可用店铺，请先配置店铺" @change="onShopChange" />
      </a-card>

      <!-- 发起同步 -->
      <a-card v-if="shopId" :bordered="false">
        <template #title>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>发起数据同步</span>
            <a-button type="primary" :loading="creating" :disabled="selectedTypes.length === 0" @click="createTasks">
              开始同步（{{ selectedTypes.length }}项）
            </a-button>
          </div>
        </template>
        <a-checkbox-group v-model:value="selectedTypes">
          <a-row :gutter="[16, 12]">
            <a-col v-for="g in syncGroups" :key="g.title" :span="8">
              <div style="margin-bottom: 6px; font-weight: 500; color: #333;">{{ g.title }}</div>
              <div v-for="t in g.items" :key="t.value" style="padding: 2px 0;">
                <a-checkbox :value="t.value">{{ t.label }}</a-checkbox>
              </div>
            </a-col>
          </a-row>
        </a-checkbox-group>
        <div v-if="selectedTypes.includes('GOODS')" class="goods-scope-panel">
          <div class="goods-scope-title">商品同步方案</div>
          <a-radio-group v-model:value="goodsSyncMode" button-style="solid">
            <a-radio-button value="LAST_WEEK">最近一周</a-radio-button>
            <a-radio-button value="LAST_YEAR">最近一年</a-radio-button>
          </a-radio-group>
          <div class="goods-scope-tip">
            最近一周用于增量更新；最近一年仅用于手动初始化，后台会按周拆分并在每周内继续分页拉取。
          </div>
        </div>
        <div v-if="selectedTypes.includes('PRICE_ADJUST')" class="goods-scope-panel">
          <div class="goods-scope-title">调价单同步方案</div>
          <a-radio-group v-model:value="priceAdjustSyncMode" button-style="solid">
            <a-radio-button value="LAST_WEEK">最近一周</a-radio-button>
            <a-radio-button value="LAST_YEAR">最近一年</a-radio-button>
          </a-radio-group>
          <div class="goods-scope-tip">
            手动同步支持最近一周和最近一年；最近一年会按周切分时间窗口分页拉取。自动同步固定读取配置中的调价单自动同步范围，默认最近7天。
          </div>
        </div>
        <div style="margin-top: 8px; color: #999; font-size: 12px;">
          提示：供货价同步依赖商品信息数据，系统会自动按顺序执行
        </div>
      </a-card>

      <a-card v-if="shopId" :bordered="false">
        <template #title>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>商品明细回填</span>
            <a-button type="primary" ghost :loading="repairingDetails" @click="repairGoodsDetails">
              回填商品明细
            </a-button>
          </div>
        </template>
        <a-alert
          message="适用于已同步完商品主表但缺失 SKU / 规格 / 条码 / 站点 / 属性明细的场景"
          description="回填会直接读取本地 raw_json，不会再次调用 TEMU 接口。当前后端按页并发执行，每页独立事务提交；执行期间请勿重复点击。"
          type="info"
          show-icon
        />
        <div v-if="repairingDetails" class="repair-progress-wrap">
          <a-progress :percent="repairProgressPercent" status="active" />
          <div class="repair-progress-text">{{ repairProgressText }}</div>
        </div>
        <a-card v-if="repairResult" size="small" class="repair-result-card">
          <a-descriptions :column="3" size="small" bordered>
            <a-descriptions-item label="任务ID">{{ repairResult.jobId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="当前状态">{{ repairStatusLabel(repairResult.status) }}</a-descriptions-item>
            <a-descriptions-item label="店铺ID">{{ repairResult.shopId }}</a-descriptions-item>
            <a-descriptions-item label="分页大小">{{ repairResult.pageSize ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="并发度">{{ repairResult.concurrency ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="总页数">{{ repairResult.totalPages ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="已提交分页">{{ repairResult.submittedPages ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="完成分页">{{ repairResult.completedPages ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="运行中分页">{{ formatRunningPages(repairResult.runningPages) }}</a-descriptions-item>
            <a-descriptions-item label="扫描商品">{{ repairResult.scanned ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="成功回填">{{ repairResult.repaired ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="跳过数量">{{ repairResult.skipped ?? 0 }}</a-descriptions-item>
            <a-descriptions-item label="失败页数">{{ (repairResult.failedPages || []).length }}</a-descriptions-item>
            <a-descriptions-item label="最近进展">{{ formatDT(repairResult.lastProgressAt) }}</a-descriptions-item>
          </a-descriptions>
          <a-alert
            v-if="repairResult.message"
            :message="repairResult.message"
            type="info"
            show-icon
            style="margin-top: 16px;"
          />
          <a-alert
            v-if="repairResult.failedPages && repairResult.failedPages.length"
            type="warning"
            show-icon
            class="repair-failed-alert"
            :message="`有 ${repairResult.failedPages.length} 个分页回填失败`"
            :description="formatFailedPages(repairResult.failedPages)"
          />
          <div v-else class="repair-success-text">本次回填未发现失败分页，可以直接继续执行供货价同步。</div>
        </a-card>
      </a-card>

      <!-- 同步任务列表 -->
      <a-card v-if="shopId" :bordered="false">
        <template #title>
          <div style="display: flex; justify-content: space-between; align-items: center;">
            <span>同步任务</span>
            <a-space>
              <a-select v-model:value="filters.syncType" style="width: 140px" allow-clear placeholder="全部类型" @change="reload">
                <a-select-option v-for="t in allSyncTypes" :key="t.value" :value="t.value">{{ t.label }}</a-select-option>
              </a-select>
              <a-button @click="reload">刷新</a-button>
            </a-space>
          </div>
        </template>
        <a-table rowKey="id" :columns="columns" :dataSource="rows" :loading="loading"
          :pagination="pagination" :scroll="{ x: 1100 }" tableLayout="fixed" @change="onTableChange">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'syncType'">
              {{ typeLabel(record.syncType) }}
              <div v-if="record.syncScope" style="color: #999; font-size: 12px; margin-top: 2px;">
                {{ syncScopeLabel(record.syncScope) }}
              </div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusLabel(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'progress'">
              <template v-if="isRunning(record.status)">
                <a-progress :percent="calcPercent(record)" size="small" :status="record.status === 'DOWNLOADING' ? 'active' : 'normal'" />
              </template>
              <template v-else-if="record.downloadTotal">
                下载 {{ record.downloadCompleted ?? 0 }}/{{ record.downloadTotal }}
                <template v-if="record.persistTotal">，入库 {{ record.persistCompleted ?? 0 }}/{{ record.persistTotal }}</template>
              </template>
              <span v-else style="color: #999">-</span>
            </template>
            <template v-else-if="column.key === 'error'">
              <a-tooltip v-if="record.lastErrorMsg" :title="record.lastErrorMsg">
                <a-tag color="red" class="ellipsis-tag">{{ record.lastErrorMsg }}</a-tag>
              </a-tooltip>
              <span v-else style="color: #999">-</span>
            </template>
            <template v-else-if="column.key === 'time'">
              <span class="mono">{{ formatDT(record.startedAt || record.createdAt) }}</span>
              <br v-if="record.finishedAt" />
              <span v-if="record.finishedAt" class="mono" style="color: #999; font-size: 12px;">耗时 {{ calcDuration(record) }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a @click="showDetail(record)">详情</a>
                <a v-if="canRetry(record.status)" @click="retryTask(record.id)">重试</a>
                <a v-if="canCancel(record.status)" style="color: #ff4d4f" @click="cancelTask(record.id)">取消</a>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <!-- 任务详情弹窗 -->
      <a-modal v-model:open="detailVisible" :title="detailTitle" width="750px" :footer="null" @cancel="stopPolling">
        <template v-if="detailData">
          <!-- 基本信息 -->
          <a-descriptions :column="3" size="small" bordered style="margin-bottom: 16px;">
            <a-descriptions-item label="任务ID">#{{ detailData.id }}</a-descriptions-item>
            <a-descriptions-item label="同步类型">{{ typeLabel(detailData.syncType) }}</a-descriptions-item>
            <a-descriptions-item v-if="detailData.syncScope" label="同步范围">{{ syncScopeLabel(detailData.syncScope) }}</a-descriptions-item>
            <a-descriptions-item label="触发方式">{{ detailData.triggerType === 'MANUAL' ? '手动' : '定时' }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detailData.status)">{{ statusLabel(detailData.status) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="创建时间">{{ formatDT(detailData.createdAt) }}</a-descriptions-item>
            <a-descriptions-item label="重试次数">{{ detailData.retryCount || 0 }}</a-descriptions-item>
          </a-descriptions>

          <!-- 下载 + 入库进度 -->
          <a-row :gutter="16" style="margin-bottom: 16px;">
            <a-col :span="12">
              <a-card size="small" title="下载进度">
                <template v-if="detailData.downloadTotal">
                  <a-progress :percent="Math.round((detailData.downloadCompleted || 0) / detailData.downloadTotal * 100)"
                    :status="detailData.currentPhase === 'DOWNLOAD' && isRunning(detailData.status) ? 'active' : 'normal'" />
                  <div style="margin-top: 4px; font-size: 12px; color: #666;">
                    {{ detailData.downloadCompleted ?? 0 }} / {{ detailData.downloadTotal }} 条
                    <span v-if="detailData.downloadFailed" style="color: #ff4d4f;">，失败 {{ detailData.downloadFailed }}</span>
                  </div>
                </template>
                <span v-else style="color: #999;">等待开始</span>
              </a-card>
            </a-col>
            <a-col :span="12">
              <a-card size="small" title="入库进度">
                <template v-if="detailData.persistTotal">
                  <a-progress :percent="Math.round((detailData.persistCompleted || 0) / detailData.persistTotal * 100)"
                    :status="detailData.currentPhase === 'PERSIST' && isRunning(detailData.status) ? 'active' : 'normal'" />
                  <div style="margin-top: 4px; font-size: 12px; color: #666;">
                    {{ detailData.persistCompleted ?? 0 }} / {{ detailData.persistTotal }} 条
                    <span v-if="detailData.persistFailed" style="color: #ff4d4f;">，失败 {{ detailData.persistFailed }}</span>
                    <span v-if="detailData.totalBatches">（批次 {{ detailData.persistedBatches ?? 0 }}/{{ detailData.totalBatches }}）</span>
                  </div>
                </template>
                <span v-else style="color: #999;">等待下载完成</span>
              </a-card>
            </a-col>
          </a-row>

          <!-- 错误信息 -->
          <a-alert v-if="detailData.lastErrorMsg" :message="detailData.lastErrorMsg" type="error" show-icon
            style="margin-bottom: 16px;" />

          <!-- 操作按钮 -->
          <div v-if="canRetry(detailData.status) || canCancel(detailData.status)" style="margin-bottom: 16px;">
            <a-space>
              <a-button v-if="canRetry(detailData.status)" type="primary" @click="retryTask(detailData.id)">
                {{ detailData.status === 'PERSIST_FAILED' ? '继续入库' : '重新下载' }}
              </a-button>
              <a-button v-if="canRetry(detailData.status)" @click="retryTaskFull(detailData.id)">全量重跑</a-button>
              <a-button v-if="canCancel(detailData.status)" danger @click="cancelTask(detailData.id)">取消任务</a-button>
            </a-space>
          </div>

          <!-- 执行日志 -->
          <a-card size="small" title="执行日志" style="max-height: 300px; overflow-y: auto;">
            <div v-if="!detailData.logs || detailData.logs.length === 0" style="color: #999; text-align: center; padding: 20px;">暂无日志</div>
            <div v-for="(log, i) in detailData.logs" :key="i" class="log-line">
              <span class="mono log-time">{{ formatDT(log.createdAt) }}</span>
              <a-tag :color="logLevelColor(log.level)" size="small" style="margin: 0 6px;">{{ log.level }}</a-tag>
              <span :class="{ 'log-phase': true }">{{ phaseLabel(log.phase) }}</span>
              <span class="log-msg">{{ log.message }}</span>
            </div>
          </a-card>
        </template>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import ShopTabs from '@/platform/components/ShopTabs.vue'
import { syncApi } from '@/platform/api/sync'
import { temuShopsApi } from '@/platform/api/temuShops'

// ==================== 常量映射 ====================
const syncTypeMap = {
  GOODS: '商品信息', LIFECYCLE: '生命周期', PRICE: '供货价格',
  FREIGHT: '运费模板', WAREHOUSE: '发货仓库',
  PRICE_REVIEW: '核价单', PRICE_ADJUST: '调价单', ACTIVITY: '营销活动'
}
const allSyncTypes = Object.entries(syncTypeMap).map(([value, label]) => ({ value, label }))
const syncGroups = [
  { title: '商品数据', items: [{ value: 'GOODS', label: '商品信息' }, { value: 'LIFECYCLE', label: '生命周期' }, { value: 'PRICE', label: '供货价格' }] },
  { title: '物流配置', items: [{ value: 'FREIGHT', label: '运费模板' }, { value: 'WAREHOUSE', label: '发货仓库' }] },
  { title: '价格与活动', items: [{ value: 'PRICE_REVIEW', label: '核价单' }, { value: 'PRICE_ADJUST', label: '调价单' }, { value: 'ACTIVITY', label: '营销活动' }] }
]

const statusMap = {
  PENDING: '等待执行', DOWNLOADING: '下载中', DOWNLOADED: '下载完成',
  DOWNLOAD_FAILED: '下载失败', PERSISTING: '入库中', PERSIST_FAILED: '入库失败',
  SUCCEEDED: '已完成', CANCELLED: '已取消'
}
const statusColor = (s) => ({
  PENDING: 'default', DOWNLOADING: 'processing', DOWNLOADED: 'cyan',
  DOWNLOAD_FAILED: 'error', PERSISTING: 'processing', PERSIST_FAILED: 'error',
  SUCCEEDED: 'success', CANCELLED: 'warning'
}[s] || 'default')
const statusLabel = (s) => statusMap[s] || s
const typeLabel = (v) => syncTypeMap[v] || v
const phaseLabel = (p) => ({ DOWNLOAD: '下载', PERSIST: '入库', GENERAL: '通用' }[p] || p)
const logLevelColor = (l) => ({ INFO: 'blue', WARN: 'orange', ERROR: 'red' }[l] || 'default')
const syncScopeLabel = (scope) => {
  if (!scope) return '-'
  if (scope === 'LAST_WEEK') return '最近一周'
  if (scope === 'LAST_YEAR') return '最近一年'
  const match = String(scope).match(/^LAST_(\d+)_DAYS$/)
  if (match) return `最近${match[1]}天`
  return scope
}

// 状态判定
const isRunning = (s) => ['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'PERSISTING'].includes(s)
const canRetry = (s) => ['DOWNLOAD_FAILED', 'PERSIST_FAILED'].includes(s)
const canCancel = (s) => ['PENDING', 'DOWNLOADING', 'DOWNLOADED', 'PERSISTING'].includes(s)

// ==================== 数据 ====================
const shopsLoading = ref(false)
const shops = ref([])
const shopId = ref(null)
const loading = ref(false)
const creating = ref(false)
const repairingDetails = ref(false)
const selectedTypes = ref([])
const goodsSyncMode = ref('LAST_WEEK')
const priceAdjustSyncMode = ref('LAST_WEEK')
const rows = ref([])
const repairResult = ref(null)
const repairJobId = ref(null)
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)
const filters = reactive({ syncType: undefined })

// 任务详情弹窗
const detailVisible = ref(false)
const detailData = ref(null)
const detailTitle = computed(() => {
  if (!detailData.value) return '任务详情'
  return `同步任务 #${detailData.value.id} — ${typeLabel(detailData.value.syncType)}`
})

// 进度轮询
let pollTimer = null
let repairPollTimer = null

const columns = [
  { title: '同步类型', key: 'syncType', width: 100 },
  { title: '状态', key: 'status', width: 100 },
  { title: '进度', key: 'progress', width: 200 },
  { title: '错误信息', key: 'error' },
  { title: '时间', key: 'time', width: 160 },
  { title: '操作', key: 'actions', width: 140, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value, pageSize: pageSize.value, total: total.value,
  showSizeChanger: true, showTotal: (t) => `共 ${t} 条`
}))

const repairProgressPercent = computed(() => {
  const totalPages = repairResult.value?.totalPages || 0
  const completedPages = repairResult.value?.completedPages || 0
  if (totalPages <= 0) return 0
  return Math.min(100, Math.round(completedPages / totalPages * 100))
})

const repairProgressText = computed(() => {
  if (!repairResult.value) return '商品明细回填执行中'
  const completedPages = repairResult.value.completedPages ?? 0
  const totalPages = repairResult.value.totalPages ?? 0
  const message = repairResult.value.message || '商品明细回填执行中'
  return `${message}（${completedPages}/${totalPages} 页）`
})

// ==================== 工具方法 ====================
const formatDT = (v) => {
  if (!v) return '-'
  if (typeof v === 'string') {
    const m = v.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})/)
    if (m) return `${m[1]}-${m[2]}-${m[3]} ${m[4]}:${m[5]}:${m[6]}`
    return v
  }
  if (Array.isArray(v) && v.length >= 6) {
    const p = (n) => String(n).padStart(2, '0')
    return `${v[0]}-${p(v[1])}-${p(v[2])} ${p(v[3])}:${p(v[4])}:${p(v[5])}`
  }
  return '-'
}

const parseDT = (v) => {
  if (!v) return 0
  if (typeof v === 'string') return new Date(v).getTime()
  if (Array.isArray(v) && v.length >= 6) return new Date(v[0], v[1] - 1, v[2], v[3], v[4], v[5]).getTime()
  return 0
}

const calcDuration = (record) => {
  if (!record.startedAt || !record.finishedAt) return isRunning(record.status) ? '进行中' : '-'
  const ms = parseDT(record.finishedAt) - parseDT(record.startedAt)
  if (ms < 1000) return ms + 'ms'
  if (ms < 60000) return (ms / 1000).toFixed(1) + '秒'
  return Math.floor(ms / 60000) + '分' + Math.round((ms % 60000) / 1000) + '秒'
}

const calcPercent = (record) => {
  if (record.currentPhase === 'PERSIST' && record.persistTotal) {
    return Math.round((record.persistCompleted || 0) / record.persistTotal * 100)
  }
  if (record.downloadTotal) {
    return Math.round((record.downloadCompleted || 0) / record.downloadTotal * 100)
  }
  return 0
}

const formatFailedPages = (failedPages) => failedPages
  .map(item => `第 ${item.page} 页：${item.errorMessage || item.error || '-'}`)
  .join('；')

const formatRunningPages = (runningPages) => {
  if (!runningPages || runningPages.length === 0) return '-'
  if (runningPages.length <= 8) return runningPages.join('、')
  return `${runningPages.slice(0, 8).join('、')} 等 ${runningPages.length} 页`
}

const repairStatusLabel = (status) => ({
  PENDING: '等待执行',
  RUNNING: '执行中',
  SUCCEEDED: '已完成',
  FAILED: '部分失败',
  IDLE: '空闲'
}[status] || status || '-')

// ==================== 数据加载 ====================
const loadShops = async () => {
  shopsLoading.value = true
  try {
    const res = await temuShopsApi.list({ enabled: true })
    shops.value = res?.success ? (res.data || []) : []
    const firstShopId = shops.value[0]?.shopId || null
    if (!firstShopId) {
      shopId.value = null
      rows.value = []
      total.value = 0
      repairResult.value = null
      repairJobId.value = null
      return
    }
    if (!shopId.value || !shops.value.some((item) => item.shopId === shopId.value)) {
      shopId.value = firstShopId
      await reload()
    }
  } catch { message.error('店铺加载失败') }
  finally { shopsLoading.value = false }
}

const onShopChange = () => { if (shopId.value) reload() }

const loadLatestRepairStatus = async () => {
  if (!shopId.value) return
  try {
    const res = await syncApi.getLatestRepairGoodsDetailsStatus(shopId.value)
    if (!res?.success || !res.data) return
    if (res.data.status === 'IDLE') {
      repairResult.value = null
      repairJobId.value = null
      return
    }
    repairResult.value = res.data
    repairJobId.value = res.data.jobId || null
    if (res.data.status === 'PENDING' || res.data.status === 'RUNNING') {
      repairingDetails.value = true
      startRepairPolling()
    }
  } catch {
    // 忽略恢复查询失败
  }
}

const fetchList = async () => {
  if (!shopId.value) return
  loading.value = true
  try {
    const res = await syncApi.listSyncTasks({
      shopId: shopId.value, syncType: filters.syncType || undefined,
      page: page.value, pageSize: pageSize.value
    })
    if (res?.success) {
      rows.value = res.data?.content || []
      total.value = res.data?.totalElements || 0
    } else { message.error(res?.message || '加载失败') }
  } catch (e) { message.error(e.message || '加载失败') }
  finally { loading.value = false }
}

const reload = () => { page.value = 1; fetchList(); loadLatestRepairStatus() }
const onTableChange = (p) => { page.value = p.current; pageSize.value = p.pageSize; fetchList() }

// ==================== 创建任务 ====================
const createTasks = async () => {
  if (selectedTypes.value.length === 0) return
  creating.value = true
  try {
    const payload = { shopId: shopId.value, syncTypes: selectedTypes.value }
    if (selectedTypes.value.includes('GOODS')) payload.goodsSyncMode = goodsSyncMode.value
    if (selectedTypes.value.includes('PRICE_ADJUST')) payload.priceAdjustSyncMode = priceAdjustSyncMode.value
    const res = await syncApi.createSyncTasks(payload)
    if (res?.success) {
      message.success('同步任务已创建')
      selectedTypes.value = []
      goodsSyncMode.value = 'LAST_WEEK'
      priceAdjustSyncMode.value = 'LAST_WEEK'
      reload()
    } else { message.error(res?.message || '创建失败') }
  } catch (e) { message.error(e.message || '创建失败') }
  finally { creating.value = false }
}

const repairGoodsDetails = () => {
  Modal.confirm({
    title: '确认回填商品明细',
    content: '回填会读取当前店铺已同步商品的 raw_json，补齐 SKU、规格、条码、站点和属性明细。执行期间请勿重复点击。',
    okText: '开始回填',
    onOk: async () => {
      repairingDetails.value = true
      repairResult.value = null
      try {
        const res = await syncApi.repairGoodsDetails(shopId.value)
        if (res?.success) {
          repairResult.value = res.data || {}
          repairJobId.value = res.data?.jobId || null
          if (res.data?.status === 'SUCCEEDED') {
            message.success('商品明细回填完成')
            repairingDetails.value = false
          } else {
            message.success('商品明细回填已启动')
            startRepairPolling()
          }
        } else {
          message.error(res?.message || '回填失败')
          repairingDetails.value = false
        }
      } catch (e) {
        message.error(e.message || '回填失败')
        repairingDetails.value = false
      }
    }
  })
}

const startRepairPolling = () => {
  stopRepairPolling()
  if (!repairJobId.value) return
  repairPollTimer = setInterval(async () => {
    try {
      const res = await syncApi.getRepairGoodsDetailsStatus(repairJobId.value)
      if (!res?.success || !res.data) return
      repairResult.value = res.data
      if (res.data.status !== 'PENDING' && res.data.status !== 'RUNNING') {
        repairingDetails.value = false
        stopRepairPolling()
        const failedCount = (res.data.failedPages || []).length
        if (res.data.status === 'FAILED') {
          message.warning(`商品明细回填结束，但有 ${failedCount} 个分页失败`)
        } else {
          message.success('商品明细回填完成')
        }
      }
    } catch {
      // 轮询失败先忽略，等待下次重试
    }
  }, 2000)
}

const stopRepairPolling = () => {
  if (repairPollTimer) {
    clearInterval(repairPollTimer)
    repairPollTimer = null
  }
}

// ==================== 任务详情 ====================
const showDetail = async (record) => {
  try {
    const res = await syncApi.getTaskDetail(record.id)
    if (res?.success) {
      detailData.value = res.data
      detailVisible.value = true
      if (isRunning(res.data.status)) startPolling(record.id)
    } else { message.error(res?.message || '加载失败') }
  } catch (e) { message.error(e.message || '加载失败') }
}

// ==================== 进度轮询 ====================
const startPolling = (taskId) => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await syncApi.getTaskProgress(taskId)
      if (res?.success && detailData.value) {
        const d = res.data
        detailData.value.status = d.status
        detailData.value.currentPhase = d.currentPhase
        detailData.value.downloadTotal = d.downloadTotal
        detailData.value.downloadCompleted = d.downloadCompleted
        detailData.value.persistTotal = d.persistTotal
        detailData.value.persistCompleted = d.persistCompleted
        detailData.value.totalBatches = d.totalBatches
        detailData.value.persistedBatches = d.persistedBatches
        // 更新最新日志
        if (d.latestLogs && d.latestLogs.length > 0) {
          const existing = detailData.value.logs || []
          const existMsgs = new Set(existing.map(l => l.message + l.createdAt))
          for (const nl of d.latestLogs) {
            if (!existMsgs.has(nl.message + nl.createdAt)) existing.push(nl)
          }
          detailData.value.logs = existing
        }
        if (!isRunning(d.status)) {
          stopPolling()
          fetchList() // 刷新列表
          // 重新载完整详情
          const full = await syncApi.getTaskDetail(taskId)
          if (full?.success) detailData.value = full.data
        }
      }
    } catch { /* 轮询异常忽略 */ }
  }, 3000)
}

const stopPolling = () => {
  if (pollTimer) { clearInterval(pollTimer); pollTimer = null }
}

// ==================== 重试/取消 ====================
const retryTask = async (taskId) => {
  try {
    const res = await syncApi.retryTask(taskId, 'CONTINUE')
    if (res?.success) {
      message.success('重试已启动')
      fetchList()
      if (detailData.value?.id === taskId) {
        detailData.value = res.data
        startPolling(taskId)
      }
    } else { message.error(res?.message || '重试失败') }
  } catch (e) { message.error(e.message || '重试失败') }
}

const retryTaskFull = (taskId) => {
  Modal.confirm({
    title: '确认全量重跑',
    content: '全量重跑将清除已下载数据，从头开始同步。确定继续？',
    onOk: async () => {
      try {
        const res = await syncApi.retryTask(taskId, 'FULL')
        if (res?.success) {
          message.success('全量重跑已启动')
          fetchList()
          if (detailData.value?.id === taskId) {
            detailData.value = res.data
            startPolling(taskId)
          }
        } else { message.error(res?.message || '重试失败') }
      } catch (e) { message.error(e.message || '重试失败') }
    }
  })
}

const cancelTask = (taskId) => {
  Modal.confirm({
    title: '确认取消任务',
    content: '取消后任务将终止。确定继续？',
    okType: 'danger',
    onOk: async () => {
      try {
        const res = await syncApi.cancelTask(taskId)
        if (res?.success) {
          message.success('任务已取消')
          fetchList()
          if (detailData.value?.id === taskId) {
            detailData.value.status = 'CANCELLED'
            stopPolling()
          }
        } else { message.error(res?.message || '取消失败') }
      } catch (e) { message.error(e.message || '取消失败') }
    }
  })
}

// ==================== 生命周期 ====================
onMounted(() => { loadShops() })
onUnmounted(() => { stopPolling(); stopRepairPolling() })
</script>

<style scoped>
.page { display: flex; flex-direction: column; gap: 16px; }
.goods-scope-panel { margin-top: 14px; padding: 12px 14px; background: #fafcff; border: 1px solid #e6f4ff; border-radius: 10px; }
.goods-scope-title { margin-bottom: 10px; font-weight: 600; color: #1f2937; }
.goods-scope-tip { margin-top: 10px; font-size: 12px; color: #667085; }
.repair-progress-wrap { margin-top: 16px; }
.repair-progress-text { margin-top: 8px; font-size: 12px; color: #667085; }
.repair-result-card { margin-top: 16px; }
.repair-failed-alert { margin-top: 16px; }
.repair-success-text { margin-top: 16px; font-size: 13px; color: #389e0d; }
.mono { font-family: 'SF Mono', 'Consolas', monospace; font-size: 12px; }
.ellipsis-tag { max-width: 200px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: inline-block; cursor: pointer; }
.log-line { padding: 3px 0; border-bottom: 1px solid #f0f0f0; display: flex; align-items: center; font-size: 13px; }
.log-line:last-child { border-bottom: none; }
.log-time { color: #999; min-width: 130px; }
.log-phase { color: #1890ff; margin-right: 8px; font-size: 12px; }
.log-phase::before { content: '['; }
.log-phase::after { content: ']'; }
.log-msg { flex: 1; }
</style>
