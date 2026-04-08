<template>
  <ProLayout title="自动化发布日志">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="head-top">
            <div class="title">TEMU 自动发布日志</div>
            <div class="head-status">
              <a-space wrap :size="8">
                <a-button size="small" :loading="workerLoading" @click="loadWorkerStatus">刷新状态</a-button>
                <a-tag v-if="worker" :color="worker.enabled ? (worker.running ? 'green' : 'gold') : 'default'">
                  Worker {{ worker.enabled ? (worker.running ? 'RUNNING' : 'STOPPED') : 'DISABLED' }}
                </a-tag>
                <a-tag v-if="worker" color="blue">pollMs {{ worker.pollMs }}</a-tag>
                <a-tag v-if="worker?.lastTickAt" color="default">lastTick {{ fmtTime(worker.lastTickAt) }}</a-tag>
                <a-tag v-if="worker?.lastClaimAt" color="default">lastClaim {{ fmtTime(worker.lastClaimAt) }}</a-tag>
                <a-tag v-if="worker?.lastError" color="red">{{ worker.lastError }}</a-tag>
              </a-space>
            </div>
          </div>

          <div class="head-filters">
            <a-space wrap :size="10">
              <a-input
                v-model:value="spuId"
                placeholder="spuId (可选)"
                style="width: 160px"
                allow-clear
                @pressEnter="reload"
              />
              <a-select v-model:value="status" placeholder="状态" style="width: 140px" allow-clear>
                <a-select-option value="STARTED">STARTED</a-select-option>
                <a-select-option value="SKIPPED">SKIPPED</a-select-option>
                <a-select-option value="SUCCEEDED">SUCCEEDED</a-select-option>
                <a-select-option value="FAILED">FAILED</a-select-option>
              </a-select>
              <a-select v-model:value="action" placeholder="动作" style="width: 120px" allow-clear>
                <a-select-option value="SKIP">SKIP</a-select-option>
                <a-select-option value="PUBLISH">PUBLISH</a-select-option>
              </a-select>
              <a-input
                v-model:value="q"
                placeholder="关键字(原因/错误)"
                style="width: 220px"
                allow-clear
                @pressEnter="search"
              />
              <a-button type="primary" :loading="loading" @click="search">查询</a-button>
              <a-button danger :loading="clearing" @click="clearAllLogs">清空数据</a-button>
            </a-space>
          </div>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="runs"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1100 }"
          size="middle"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'action'">
              <a-tag :color="record.action === 'PUBLISH' ? 'blue' : 'default'">{{ record.action || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'publishRunId'">
              <span class="mono">{{ record.publishRunId || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                <div>start: <span class="mono">{{ fmtTime(record.startedAt) || '-' }}</span></div>
                <div>end: <span class="mono">{{ fmtTime(record.finishedAt) || '-' }}</span></div>
              </div>
            </template>
            <template v-else-if="column.key === 'summary'">
              <div class="summary">
                <div class="s">{{ record.summary || '-' }}</div>
                <div v-if="record.error" class="e">{{ record.error }}</div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openLogs(record)">查看详细日志</a-button>
                <a-button
                  v-if="record.publishRunId"
                  size="small"
                  type="primary"
                  ghost
                  @click="jumpPublishRun(record)"
                >
                  打开发布 run
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>

        <a-alert
          v-if="worker"
          class="hint"
          type="info"
          show-icon
          message="页面说明"
          :description="
            'Worker RUNNING 代表后台线程在跑，lastTick 会持续更新。只有 claim 到候选 spu 才会生成 run 记录。\n' +
            'SKIPPED 代表规则不满足；点查看详细日志可看到每条规则的 expected/actual。'
          "
        />
      </a-card>

      <a-drawer
        v-model:open="logsOpen"
        :title="drawerTitle"
        width="920"
        :destroyOnClose="true"
      >
        <div class="drawer-meta" v-if="sample">
          <a-descriptions size="small" bordered :column="2">
            <a-descriptions-item label="spuId">
              <span class="mono">{{ sample.spuId }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(sample.status)">{{ sample.status }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="动作">
              <a-tag :color="sample.action === 'PUBLISH' ? 'blue' : 'default'">{{ sample.action }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="publishRunId">
              <span class="mono">{{ sample.publishRunId || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="开始">
              <span class="mono">{{ fmtTime(sample.startedAt) || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="结束">
              <span class="mono">{{ fmtTime(sample.finishedAt) || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="摘要" :span="2">
              <div class="pre-like">{{ sample.summary || '-' }}</div>
            </a-descriptions-item>
            <a-descriptions-item v-if="sample.error" label="错误" :span="2">
              <div class="pre-like err">{{ sample.error }}</div>
            </a-descriptions-item>
            <a-descriptions-item v-if="sample.eligibilityJson" label="规则判定" :span="2">
              <pre class="pre">{{ pretty(sample.eligibilityJson) }}</pre>
            </a-descriptions-item>
          </a-descriptions>
        </div>

        <a-table
          rowKey="id"
          :columns="logColumns"
          :dataSource="logs"
          :loading="logsLoading"
          :pagination="false"
          size="small"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'level'">
              <a-tag :color="levelColor(record.level)">{{ record.level || 'INFO' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'msg'">
              <div class="msg">
                <div class="m">{{ record.message }}</div>
                <pre v-if="record.dataJson" class="pre">{{ pretty(record.dataJson) }}</pre>
              </div>
            </template>
          </template>
        </a-table>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import { useRoute, useRouter } from 'vue-router'
import ProLayout from '@/platform/components/ProLayout.vue'
import { temuAutoPublishLogsApi } from '@/platform/api/temuAutoPublishLogs'

const route = useRoute()
const router = useRouter()

const workerLoading = ref(false)
const worker = ref(null)
const clearing = ref(false)

const loading = ref(false)
const runs = ref([])

const spuId = ref('')
const status = ref(undefined)
const action = ref(undefined)
const q = ref('')

const page = ref(1)
const pageSize = ref(20)
const total = ref(0)

const columns = [
  { title: 'runId', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: '动作', key: 'action', width: 110 },
  { title: '状态', key: 'status', width: 110 },
  { title: 'publishRunId', key: 'publishRunId', width: 120 },
  { title: '摘要/错误', key: 'summary', width: 420 },
  { title: '时间', key: 'time', width: 260 },
  { title: '操作', key: 'actions', width: 210, fixed: 'right' }
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const statusColor = (s) => {
  if (s === 'SUCCEEDED') return 'green'
  if (s === 'FAILED') return 'red'
  if (s === 'SKIPPED') return 'default'
  return 'blue'
}

const reload = async () => {
  loading.value = true
  try {
    const params = {
      spuId: String(spuId.value || '').trim() || undefined,
      status: status.value || undefined,
      action: action.value || undefined,
      q: String(q.value || '').trim() || undefined,
      page: Math.max(page.value - 1, 0),
      size: pageSize.value
    }
    const res = await temuAutoPublishLogsApi.searchRuns(params)
    if (res?.success) {
      runs.value = Array.isArray(res.data?.content) ? res.data.content : []
      total.value = Number(res.data?.totalElements || 0)
      if (!runs.value.length) message.info('暂无记录')
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const search = () => {
  page.value = 1
  reload()
}

const loadWorkerStatus = async () => {
  workerLoading.value = true
  try {
    const res = await temuAutoPublishLogsApi.workerStatus()
    if (res?.success) {
      worker.value = res.data || null
      return
    }
    message.error(res?.message || '加载 Worker 状态失败')
  } catch (e) {
    message.error(e.message || '加载 Worker 状态失败')
  } finally {
    workerLoading.value = false
  }
}

const onTableChange = (pager) => {
  page.value = pager?.current || 1
  pageSize.value = pager?.pageSize || 20
  reload()
}

const clearAllLogs = () => {
  Modal.confirm({
    title: '确认清空自动发布日志',
    content: '会删除 TEMU 自动发布 run 记录和对应明细日志。清空后无法恢复，但不影响已发布商品数据。',
    okText: '确认清空',
    okButtonProps: { danger: true },
    onOk: async () => {
      clearing.value = true
      try {
        const res = await temuAutoPublishLogsApi.clearAllLogs()
        if (res?.success) {
          logsOpen.value = false
          logs.value = []
          sample.value = null
          selectedRun.value = null
          runs.value = []
          total.value = 0
          page.value = 1
          message.success(res.message || '自动发布日志已清空')
          await reload()
          return
        }
        message.error(res?.message || '清空失败')
      } catch (e) {
        message.error(e.message || '清空失败')
      } finally {
        clearing.value = false
      }
    }
  })
}

const logsOpen = ref(false)
const logsLoading = ref(false)
const logs = ref([])
const sample = ref(null)
const selectedRun = ref(null)

const drawerTitle = computed(() => {
  const r = selectedRun.value
  if (!r) return '自动化发布日志'
  return `自动化发布 runId=${r.id} spuId=${r.spuId} 状态=${r.status || '-'} 动作=${r.action || '-'}`
})

const logColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '阶段', dataIndex: 'stage', key: 'stage', width: 140 },
  { title: '级别', key: 'level', width: 90 },
  { title: '内容', key: 'msg' }
]

const levelColor = (l) => {
  if (l === 'ERROR') return 'red'
  if (l === 'WARN') return 'gold'
  return 'blue'
}

const openLogs = async (r) => {
  selectedRun.value = r
  logsOpen.value = true
  logs.value = []
  sample.value = null
  logsLoading.value = true
  try {
    const [res1, res2] = await Promise.all([
      temuAutoPublishLogsApi.listLogs(r.id),
      temuAutoPublishLogsApi.sample(r.id)
    ])
    if (res1?.success) logs.value = Array.isArray(res1.data) ? res1.data : []
    if (res2?.success) sample.value = res2.data || null
  } catch (e) {
    message.error(e.message || '加载日志失败')
  } finally {
    logsLoading.value = false
  }
}

const jumpPublishRun = (r) => {
  const id = r?.spuId
  if (!id) return
  router.push({ path: '/platform/publish-logs', query: { spuId: String(id) } })
}

const pretty = (s) => {
  if (!s) return ''
  const txt = String(s)
  try {
    const obj = JSON.parse(txt)
    return JSON.stringify(obj, null, 2)
  } catch {
    return txt
  }
}

const fmtTime = (v) => {
  if (!v) return ''
  // spring-boot + jackson can serialize LocalDateTime as ISO string, but older data might be array
  if (Array.isArray(v)) {
    const [y, m, d, hh, mm, ss, ns] = v
    if (!y || !m || !d) return String(v)
    const pad = (n, w = 2) => String(n || 0).padStart(w, '0')
    const ms = ns == null ? 0 : Math.floor(Number(ns) / 1e6)
    return `${y}-${pad(m)}-${pad(d)} ${pad(hh)}:${pad(mm)}:${pad(ss)}.${pad(ms, 3)}`
  }
  return String(v)
}

onMounted(() => {
  const qs = route.query || {}
  const qSpuId = qs.spuId == null ? '' : String(qs.spuId).trim()
  if (qSpuId) spuId.value = qSpuId
  loadWorkerStatus()
  reload()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.card {
  border-radius: 12px;
}

.head {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-bottom: 12px;
}

.head-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
}

.head-status {
  flex: 1;
  display: flex;
  justify-content: flex-end;
  min-width: 320px;
}

.head-filters {
  display: flex;
  justify-content: flex-start;
}

.title {
  font-weight: 700;
  font-size: 16px;
  white-space: nowrap;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.time {
  font-size: 12px;
  line-height: 1.4;
  color: rgba(0, 0, 0, 0.75);
}

.summary .s {
  line-height: 1.35;
}

.summary .e {
  margin-top: 6px;
  color: #b42318;
  white-space: pre-wrap;
}

.drawer-meta {
  margin-bottom: 12px;
}

.pre-like {
  white-space: pre-wrap;
  word-break: break-word;
}

.pre-like.err {
  color: #b42318;
}

.msg .m {
  font-weight: 600;
}

.pre {
  margin-top: 6px;
  padding: 10px;
  background: #0b1220;
  color: #e6edf3;
  border-radius: 8px;
  overflow: auto;
  max-height: 360px;
}

.hint {
  margin-top: 12px;
  border-radius: 12px;
}

.hint :deep(.ant-alert-description) {
  white-space: pre-line;
}
</style>
