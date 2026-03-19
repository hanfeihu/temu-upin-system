<template>
  <ProLayout title="发布日志">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">TEMU 发布日志</div>
          <a-space>
            <a-input
              v-model:value="spuId"
              placeholder="输入 spuId，例如 73"
              style="width: 220px"
              allow-clear
              @pressEnter="reload"
            />
            <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="runs"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 900 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'goodsId'">
              <span class="mono">{{ record.goodsId || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                 <div>start: <span class="mono">{{ fmtTime(record.startedAt) || '-' }}</span></div>
                 <div>end: <span class="mono">{{ fmtTime(record.finishedAt) || '-' }}</span></div>
               </div>
             </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openLogs(record)">查看日志</a-button>
                <a-button size="small" type="primary" ghost @click="openRaw(record)">请求/响应</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer
        v-model:open="logsOpen"
        :title="drawerTitle"
        width="860"
        :destroyOnClose="true"
      >
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

      <a-drawer
        v-model:open="rawOpen"
        title="请求 / 响应"
        width="920"
        :destroyOnClose="true"
      >
        <div class="raw">
          <div class="raw-section">
            <div class="raw-title">请求 JSON</div>
            <pre class="pre">{{ pretty(raw.requestJson) }}</pre>
          </div>
          <div class="raw-section">
            <div class="raw-title">响应 Raw</div>
            <pre class="pre">{{ pretty(raw.responseRaw) }}</pre>
          </div>
          <div class="raw-section" v-if="raw.error">
            <div class="raw-title">错误</div>
            <pre class="pre">{{ raw.error }}</pre>
          </div>
        </div>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { publishLogsApi } from '@/platform/api/publishLogs'
import { useRoute } from 'vue-router'

const spuId = ref('')
const loading = ref(false)
const runs = ref([])

const route = useRoute()

const columns = [
  { title: 'runId', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: '状态', key: 'status', width: 110 },
  { title: 'goodsId', key: 'goodsId', width: 190 },
  { title: '时间', key: 'time' },
  { title: '操作', key: 'actions', width: 180, fixed: 'right' }
]

const statusColor = (s) => {
  if (s === 'SUCCEEDED') return 'green'
  if (s === 'FAILED') return 'red'
  return 'blue'
}

const reload = async () => {
  const id = String(spuId.value || '').trim()
  // If spuId empty, show recent runs (backend returns top 50)
  try {
    if (id) localStorage.setItem('temuPublishLogs.spuId', id)
  } catch {}
  loading.value = true
  try {
    const res = await publishLogsApi.listRuns(id)
    if (res?.success) {
      runs.value = Array.isArray(res.data) ? res.data : []
      if (!runs.value.length) message.info(id ? '没有找到该 spuId 的发布记录' : '暂无发布记录')
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const logsOpen = ref(false)
const logsLoading = ref(false)
const logs = ref([])
const selectedRun = ref(null)

const drawerTitle = computed(() => {
  const r = selectedRun.value
  if (!r) return '发布日志'
  return `发布日志 runId=${r.id} spuId=${r.spuId} 状态=${r.status || '-'}`
})

const logColumns = [
  { title: '时间', dataIndex: 'createdAt', key: 'createdAt', width: 190 },
  { title: '阶段', dataIndex: 'stage', key: 'stage', width: 120 },
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
  logsLoading.value = true
  try {
    const res = await publishLogsApi.listLogs(r.id)
    if (res?.success) {
      logs.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载日志失败')
  } catch (e) {
    message.error(e.message || '加载日志失败')
  } finally {
    logsLoading.value = false
  }
}

const rawOpen = ref(false)
const raw = ref({ requestJson: '', responseRaw: '', error: '' })

const openRaw = (r) => {
  raw.value = {
    requestJson: r.requestJson || '',
    responseRaw: r.responseRaw || '',
    error: r.error || ''
  }
  rawOpen.value = true
}

const pretty = (s) => {
  if (!s) return ''
  const txt = String(s)
  // If already a JSON string, pretty print
  try {
    const obj = JSON.parse(txt)
    return JSON.stringify(obj, null, 2)
  } catch {
    return txt
  }
}

const fmtTime = (v) => {
  if (!v) return ''
  if (Array.isArray(v)) {
    const [y, m, d, hh, mm, ss, ns] = v
    if (!y || !m || !d) return String(v)
    const pad = (n, w = 2) => String(n || 0).padStart(w, '0')
    const ms = ns == null ? 0 : Math.floor(Number(ns) / 1e6)
    return `${y}-${pad(m)}-${pad(d)} ${pad(hh)}:${pad(mm)}:${pad(ss)}.${pad(ms, 3)}`
  }
  return String(v)
}

watch(
  () => spuId.value,
  (v) => {
    const id = String(v || '').trim()
    if (!id) return
    try {
      localStorage.setItem('temuPublishLogs.spuId', id)
    } catch {}
  }
)

onMounted(() => {
  const q = route.query || {}
  const qSpuId = q.spuId == null ? '' : String(q.spuId).trim()
  if (qSpuId) {
    spuId.value = qSpuId
    reload()
    return
  }
  try {
    const last = String(localStorage.getItem('temuPublishLogs.spuId') || '').trim()
    if (last) {
      spuId.value = last
      reload()
      return
    }
  } catch {}

  // default: load recent runs even without spuId
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
  border-radius: 14px;
}

.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.title {
  font-weight: 900;
  color: #0f172a;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.time {
  display: grid;
  gap: 4px;
  color: rgba(15, 23, 42, 0.78);
  font-size: 12px;
}

.msg {
  display: grid;
  gap: 8px;
}

.m {
  font-weight: 700;
  color: rgba(15, 23, 42, 0.86);
}

.pre {
  margin: 0;
  padding: 10px 12px;
  background: #0b1220;
  color: rgba(255, 255, 255, 0.86);
  border-radius: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.35;
}

.raw {
  display: grid;
  gap: 14px;
}

.raw-title {
  font-weight: 900;
  margin-bottom: 8px;
  color: rgba(15, 23, 42, 0.86);
}
</style>
