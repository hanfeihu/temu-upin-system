<template>
  <ProLayout title="发布成功案例">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">TEMU 发布成功案例</div>
          <a-space wrap>
            <a-input
              v-model:value="spuId"
              placeholder="按 spuId 查询"
              style="width: 180px"
              allow-clear
              @pressEnter="reload"
            />
            <a-input
              v-model:value="temuCatid"
              placeholder="按类目链路查询，如 27011,28946"
              style="width: 320px"
              allow-clear
              @pressEnter="reload"
            />
            <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1100 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'goodsId'">
              <span class="mono">{{ record.goodsId || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'temuCatid'">
              <div class="cat-cell">
                <div class="mono">{{ record.temuCatid || '-' }}</div>
                <div class="sub">{{ record.temuCatname || '-' }}</div>
              </div>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                <div>发布: <span class="mono">{{ fmtTime(record.publishedAt) || '-' }}</span></div>
                <div>归档: <span class="mono">{{ fmtTime(record.createdAt) || '-' }}</span></div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openDetail(record)">查看详情</a-button>
                <a-button size="small" type="primary" ghost @click="openRaw(record)">请求/响应</a-button>
                <a-button
                  v-if="record.publishRunId"
                  size="small"
                  @click="jumpPublishRun(record)"
                >
                  打开 run
                </a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-drawer
        v-model:open="detailOpen"
        :title="detailTitle"
        width="860"
        :destroyOnClose="true"
      >
        <a-skeleton v-if="detailLoading" active :paragraph="{ rows: 8 }" />
        <div v-else-if="detail" class="detail">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item label="caseId">
              <span class="mono">{{ detail.id }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="publishRunId">
              <span class="mono">{{ detail.publishRunId || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="spuId">
              <span class="mono">{{ detail.spuId }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="productId">
              <span class="mono">{{ detail.productId || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="商品标题">{{ detail.productName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="TEMU 类目链路">
              <div class="mono">{{ detail.temuCatid || '-' }}</div>
              <div class="sub">{{ detail.temuCatname || '-' }}</div>
            </a-descriptions-item>
            <a-descriptions-item label="goodsId">
              <span class="mono">{{ detail.goodsId || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="发布时间">
              <span class="mono">{{ fmtTime(detail.publishedAt) || '-' }}</span>
            </a-descriptions-item>
            <a-descriptions-item label="归档时间">
              <span class="mono">{{ fmtTime(detail.createdAt) || '-' }}</span>
            </a-descriptions-item>
          </a-descriptions>

          <a-divider />

          <a-tabs>
            <a-tab-pane key="request" tab="AddGloGoodsRequest">
              <pre class="pre">{{ pretty(detail.requestJson) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="response" tab="返回值 Raw">
              <pre class="pre">{{ pretty(detail.responseRaw) }}</pre>
            </a-tab-pane>
          </a-tabs>
        </div>
      </a-drawer>

      <a-drawer
        v-model:open="rawOpen"
        title="请求 / 响应"
        width="960"
        :destroyOnClose="true"
      >
        <div class="raw">
          <div class="raw-section">
            <div class="raw-title">AddGloGoodsRequest</div>
            <pre class="pre">{{ pretty(raw.requestJson) }}</pre>
          </div>
          <div class="raw-section">
            <div class="raw-title">响应 Raw</div>
            <pre class="pre">{{ pretty(raw.responseRaw) }}</pre>
          </div>
        </div>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { temuPublishSuccessCasesApi } from '@/platform/api/temuPublishSuccessCases'

const router = useRouter()

const spuId = ref('')
const temuCatid = ref('')
const loading = ref(false)
const rows = ref([])

const columns = [
  { title: 'caseId', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'runId', dataIndex: 'publishRunId', key: 'publishRunId', width: 100 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: 'goodsId', key: 'goodsId', width: 170 },
  { title: '商品标题', dataIndex: 'productName', key: 'productName', width: 280, ellipsis: true },
  { title: '类目链路', key: 'temuCatid', width: 300 },
  { title: '时间', key: 'time', width: 250 },
  { title: '操作', key: 'actions', width: 240, fixed: 'right' }
]

const reload = async () => {
  loading.value = true
  try {
    const params = {}
    const id = String(spuId.value || '').trim()
    const catid = String(temuCatid.value || '').trim()
    if (id) params.spuId = id
    if (catid) params.temuCatid = catid
    const res = await temuPublishSuccessCasesApi.list(params)
    if (res?.success) {
      rows.value = Array.isArray(res.data) ? res.data : []
      if (!rows.value.length) message.info('暂无成功案例')
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const detailOpen = ref(false)
const detailLoading = ref(false)
const detail = ref(null)

const detailTitle = computed(() => {
  if (!detail.value) return '成功案例详情'
  return `成功案例 #${detail.value.id} / spuId=${detail.value.spuId}`
})

const openDetail = async (record) => {
  if (!record?.id) return
  detailOpen.value = true
  detailLoading.value = true
  detail.value = null
  try {
    const res = await temuPublishSuccessCasesApi.get(record.id)
    if (res?.success) {
      detail.value = res.data
      return
    }
    message.error(res?.message || '加载详情失败')
  } catch (e) {
    message.error(e.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

const rawOpen = ref(false)
const raw = ref({ requestJson: '', responseRaw: '' })

const openRaw = async (record) => {
  if (!record?.id) return
  try {
    const res = await temuPublishSuccessCasesApi.get(record.id)
    if (res?.success) {
      raw.value = {
        requestJson: res.data?.requestJson || '',
        responseRaw: res.data?.responseRaw || ''
      }
      rawOpen.value = true
      return
    }
    message.error(res?.message || '加载详情失败')
  } catch (e) {
    message.error(e.message || '加载详情失败')
  }
}

const jumpPublishRun = (record) => {
  if (!record?.spuId) return
  router.push({
    path: '/platform/publish-logs',
    query: { spuId: String(record.spuId) }
  })
}

const pretty = (value) => {
  if (!value) return ''
  const txt = String(value)
  try {
    return JSON.stringify(JSON.parse(txt), null, 2)
  } catch {
    return txt
  }
}

const fmtTime = (value) => {
  if (!value) return ''
  if (Array.isArray(value)) {
    const [y, m, d, hh, mm, ss, ns] = value
    const pad = (n, w = 2) => String(n || 0).padStart(w, '0')
    const ms = ns == null ? 0 : Math.floor(Number(ns) / 1e6)
    return `${y}-${pad(m)}-${pad(d)} ${pad(hh)}:${pad(mm)}:${pad(ss)}.${pad(ms, 3)}`
  }
  return String(value)
}

onMounted(() => {
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
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.title {
  font-weight: 900;
  color: #0f172a;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.sub {
  margin-top: 4px;
  color: rgba(15, 23, 42, 0.6);
  font-size: 12px;
}

.cat-cell {
  display: grid;
  gap: 4px;
}

.time {
  display: grid;
  gap: 4px;
  color: rgba(15, 23, 42, 0.78);
  font-size: 12px;
}

.detail,
.raw {
  display: grid;
  gap: 14px;
}

.raw-section {
  display: grid;
  gap: 8px;
}

.raw-title {
  font-weight: 800;
  color: #0f172a;
}

.pre {
  margin: 0;
  padding: 10px 12px;
  background: #0b1220;
  color: rgba(255, 255, 255, 0.88);
  border-radius: 12px;
  overflow: auto;
  font-size: 12px;
  line-height: 1.4;
  white-space: pre-wrap;
}
</style>