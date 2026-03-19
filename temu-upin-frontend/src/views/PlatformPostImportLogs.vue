<template>
  <ProLayout title="自动化日志">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">Post-Import Automation 日志</div>
          <a-space>
            <a-input
              v-model:value="spuId"
              placeholder="输入 spuId，例如 107"
              style="width: 220px"
              allow-clear
              @pressEnter="reload"
            />
            <a-select v-model:value="status" style="width: 140px" allow-clear placeholder="状态">
              <a-select-option value="SUCCEEDED">成功</a-select-option>
              <a-select-option value="FAILED">失败</a-select-option>
              <a-select-option value="STARTED">进行中</a-select-option>
            </a-select>
            <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="filteredRuns"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 980 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ record.status || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                <div>start: <span class="mono">{{ record.startedAt || '-' }}</span></div>
                <div>end: <span class="mono">{{ record.finishedAt || '-' }}</span></div>
              </div>
            </template>
            <template v-else-if="column.key === 'summary'">
              <div class="summary">
                <div class="sum" :title="record.summary || ''">{{ record.summary || '-' }}</div>
                <div v-if="record.error" class="err" :title="record.error">{{ record.error }}</div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openLogs(record)">查看日志</a-button>
                <a-button size="small" type="primary" ghost @click="copySample(record)">一键复制样本</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer
        v-model:open="logsOpen"
        :title="drawerTitle"
        width="900"
        :destroyOnClose="true"
      >
        <div class="drawer-actions">
          <a-button :disabled="!selectedRun" @click="copySample(selectedRun)">复制该 run 样本</a-button>
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
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { postImportLogsApi } from '@/platform/api/postImportLogs'
import { useRoute } from 'vue-router'

const spuId = ref('')
const status = ref(undefined)
const loading = ref(false)
const runs = ref([])

const route = useRoute()

const columns = [
  { title: 'runId', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: '状态', key: 'status', width: 110 },
  { title: '时间', key: 'time', width: 260 },
  { title: '结果', key: 'summary' },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' }
]

const statusColor = (s) => {
  if (s === 'SUCCEEDED') return 'green'
  if (s === 'FAILED') return 'red'
  if (s === 'STARTED') return 'blue'
  return 'default'
}

const filteredRuns = computed(() => {
  const s = status.value
  if (!s) return runs.value
  return (runs.value || []).filter(r => r && r.status === s)
})

const reload = async () => {
  const id = String(spuId.value || '').trim()
  loading.value = true
  try {
    const res = await postImportLogsApi.listRuns(id)
    if (res?.success) {
      runs.value = Array.isArray(res.data) ? res.data : []
      if (!runs.value.length) message.info(id ? '没有找到该 spuId 的自动化记录' : '暂无自动化记录')
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
  if (!r) return '自动化日志'
  return `自动化日志 runId=${r.id} spuId=${r.spuId} 状态=${r.status || '-'}`
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
    const res = await postImportLogsApi.listLogs(r.id)
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

const writeClipboard = async (text) => {
  if (!text) return false
  try {
    if (navigator?.clipboard?.writeText) {
      await navigator.clipboard.writeText(String(text))
      return true
    }
  } catch {}
  try {
    const ta = document.createElement('textarea')
    ta.value = String(text)
    ta.style.position = 'fixed'
    ta.style.opacity = '0'
    document.body.appendChild(ta)
    ta.select()
    document.execCommand('copy')
    document.body.removeChild(ta)
    return true
  } catch {
    return false
  }
}

const copySample = async (r) => {
  if (!r?.id) return
  try {
    const res = await postImportLogsApi.getSample(r.id)
    if (!res?.success) {
      message.error(res?.message || '获取样本失败')
      return
    }
    const sample = res.data?.sampleJson
    if (!sample) {
      message.error('样本为空（可能该 run 还没结束）')
      return
    }
    const ok = await writeClipboard(pretty(sample))
    if (ok) message.success('样本已复制到剪贴板')
    else message.error('复制失败')
  } catch (e) {
    message.error(e.message || '获取样本失败')
  }
}

onMounted(() => {
  const q = route.query || {}
  const qSpuId = q.spuId == null ? '' : String(q.spuId).trim()
  if (qSpuId) {
    spuId.value = qSpuId
    reload()
    return
  }
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
  gap: 12px;
  margin-bottom: 10px;
}

.title {
  font-weight: 900;
  color: rgba(15, 23, 42, 0.9);
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace;
}

.time {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.72);
}

.summary .sum {
  color: rgba(15, 23, 42, 0.82);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 520px;
}

.summary .err {
  margin-top: 4px;
  color: rgba(239, 68, 68, 0.95);
  font-size: 12px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 520px;
}

.drawer-actions {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 10px;
}

.msg .m {
  color: rgba(15, 23, 42, 0.86);
}

.pre {
  margin-top: 8px;
  padding: 10px;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.04);
  overflow: auto;
}
</style>
