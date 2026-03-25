<template>
  <ProLayout title="TEMU 类目属性AI填写任务">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">任务列表</div>
          <a-space>
            <a-input v-model:value="q" placeholder="按商品标题搜索" style="width: 240px" allow-clear @pressEnter="reload" />
            <a-select v-model:value="status" style="width: 160px" allow-clear placeholder="状态">
              <a-select-option :value="0">待开始</a-select-option>
              <a-select-option :value="1">执行中</a-select-option>
              <a-select-option :value="2">已完成</a-select-option>
            </a-select>
            <a-button type="primary" @click="openCreate">添加任务</a-button>
            <a-button @click="reload" :loading="loading">刷新</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1480 }"
          size="middle"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'img'">
              <a-image v-if="record.productMainImage" :src="record.productMainImage" :width="54" :height="54" :preview="true" />
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="statusColor(record.status)">{{ statusText(record.status) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'time'">
              <div class="time">
                <div>start: <span class="mono">{{ fmtTime(record.startedAt) || '-' }}</span></div>
                <div>end: <span class="mono">{{ fmtTime(record.finishedAt) || '-' }}</span></div>
              </div>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openDetail(record)">详情</a-button>
                <a-button size="small" type="primary" ghost @click="runNow(record)" :loading="runningId === record.id">立即执行</a-button>
              </a-space>
            </template>
          </template>
        </a-table>

        <div class="pager">
          <a-pagination
            :current="page + 1"
            :pageSize="size"
            :total="total"
            show-size-changer
            :pageSizeOptions="['10','20','50']"
            @change="onPageChange"
            @showSizeChange="onPageSizeChange"
          />
        </div>
      </a-card>

      <a-modal v-model:open="createOpen" title="添加任务" :confirm-loading="creating" @ok="create" width="760">
        <a-form layout="vertical">
          <a-form-item label="搜索商品（按标题）">
            <a-input v-model:value="productQ" placeholder="输入商品标题关键词" allow-clear @pressEnter="searchProducts" />
          </a-form-item>
          <a-form-item>
            <a-button @click="searchProducts" :loading="productLoading">搜索</a-button>
          </a-form-item>

          <a-table
            rowKey="id"
            :columns="productColumns"
            :dataSource="productRows"
            :loading="productLoading"
            :pagination="false"
            size="small"
            :rowSelection="rowSelection"
          >
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'img'">
                <a-image v-if="record.productMainImage" :src="record.productMainImage" :width="48" :height="48" :preview="true" />
                <span v-else>-</span>
              </template>
            </template>
          </a-table>
        </a-form>
      </a-modal>

      <a-drawer v-model:open="detailOpen" :title="detailTitle" width="980" :destroyOnClose="true">
        <div v-if="detail" class="detail">
          <a-descriptions bordered size="small" :column="1">
            <a-descriptions-item label="spuId">{{ detail.spuId }}</a-descriptions-item>
            <a-descriptions-item label="商品标题">{{ detail.productName }}</a-descriptions-item>
            <a-descriptions-item label="状态">
              <a-tag :color="statusColor(detail.status)">{{ statusText(detail.status) }}</a-tag>
            </a-descriptions-item>
            <a-descriptions-item label="leafCatId">{{ detail.leafCatId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="摘要">{{ detail.resultSummary || '-' }}</a-descriptions-item>
            <a-descriptions-item label="错误" v-if="detail.errorMsg">{{ detail.errorMsg }}</a-descriptions-item>
            <a-descriptions-item label="start">{{ fmtTime(detail.startedAt) || '-' }}</a-descriptions-item>
            <a-descriptions-item label="end">{{ fmtTime(detail.finishedAt) || '-' }}</a-descriptions-item>
          </a-descriptions>

          <a-divider />

          <a-tabs>
            <a-tab-pane key="template" tab="模版Raw">
              <pre class="pre">{{ pretty(detail.templateRaw) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="raw" tab="AI返回原始值">
              <pre class="pre">{{ pretty(detail.responseRaw) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="parsed" tab="解析后的JSON">
              <pre class="pre">{{ pretty(detail.parsedJson) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="rules" tab="规则动作">
              <pre class="pre">{{ pretty(detail.ruleActions) }}</pre>
            </a-tab-pane>
            <a-tab-pane key="result" tab="TEMU保存格式JSON">
              <pre class="pre">{{ pretty(detail.resultJson) }}</pre>
            </a-tab-pane>
          </a-tabs>
        </div>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { temuAttrAiFillApi } from '@/platform/api/temuAttrAiFill'
import { productCollectionApi } from '@/platform/api/productCollections'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(0)
const size = ref(20)
const q = ref('')
const status = ref(undefined)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: '主图', key: 'img', width: 80 },
  { title: '商品标题', dataIndex: 'productName', key: 'productName', width: 460, ellipsis: true },
  { title: '状态', key: 'status', width: 110 },
  { title: 'leafCatId', dataIndex: 'leafCatId', key: 'leafCatId', width: 110 },
  { title: '摘要', dataIndex: 'resultSummary', key: 'resultSummary', width: 260 },
  { title: '时间', key: 'time', width: 260 },
  { title: '操作', key: 'actions', width: 200, fixed: 'right' }
]

const statusText = (s) => {
  if (s === 0) return '待开始'
  if (s === 1) return '执行中'
  if (s === 2) return '已完成'
  return '-'
}

const statusColor = (s) => {
  if (s === 2) return 'green'
  if (s === 1) return 'blue'
  if (s === 0) return 'default'
  return 'default'
}

const reload = async () => {
  loading.value = true
  try {
    const res = await temuAttrAiFillApi.listTasks({
      q: (q.value || '').trim() || undefined,
      status: status.value,
      page: page.value,
      size: size.value
    })
    if (res?.success) {
      const p = res.data
      rows.value = Array.isArray(p?.content) ? p.content : []
      total.value = Number(p?.totalElements || 0)
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const onPageChange = (p) => {
  page.value = Math.max(0, Number(p || 1) - 1)
  reload()
}

const onPageSizeChange = (_p, ps) => {
  size.value = Number(ps || 20)
  page.value = 0
  reload()
}

const runningId = ref(null)
const runNow = async (r) => {
  if (!r?.id) return
  runningId.value = r.id
  try {
    const res = await temuAttrAiFillApi.runTask(r.id)
    if (res?.success) {
      message.success('已触发执行')
      await reload()
      return
    }
    message.error(res?.message || '执行失败')
  } catch (e) {
    message.error(e.message || '执行失败')
  } finally {
    runningId.value = null
  }
}

const createOpen = ref(false)
const creating = ref(false)
const productQ = ref('')
const productLoading = ref(false)
const productRows = ref([])
const selectedProductId = ref(null)

const productColumns = [
  { title: '主图', key: 'img', width: 70 },
  { title: 'spuId', dataIndex: 'id', key: 'id', width: 90 },
  { title: '商品标题', dataIndex: 'productName', key: 'productName' }
]

const rowSelection = computed(() => ({
  type: 'radio',
  selectedRowKeys: selectedProductId.value ? [selectedProductId.value] : [],
  onChange: (keys) => {
    selectedProductId.value = Array.isArray(keys) && keys.length ? keys[0] : null
  }
}))

const openCreate = async () => {
  createOpen.value = true
  selectedProductId.value = null
  productRows.value = []
  productQ.value = ''
  await searchProducts(true)
}

const searchProducts = async (initial = false) => {
  productLoading.value = true
  try {
    const qq = initial ? '' : (productQ.value || '').trim()
    const res = await productCollectionApi.list({ q: qq, page: 0, size: 10 })
    if (res?.success) {
      const p = res.data
      productRows.value = Array.isArray(p?.content) ? p.content : []
      return
    }
    message.error(res?.message || '搜索失败')
  } catch (e) {
    message.error(e.message || '搜索失败')
  } finally {
    productLoading.value = false
  }
}

const create = async () => {
  if (!selectedProductId.value) {
    message.warning('请选择一个商品')
    return
  }
  creating.value = true
  try {
    const res = await temuAttrAiFillApi.createTask(selectedProductId.value)
    if (res?.success) {
      message.success('已创建')
      createOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '创建失败')
  } catch (e) {
    message.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

const detailOpen = ref(false)
const detail = ref(null)
const detailTitle = computed(() => {
  if (!detail.value) return '任务详情'
  return `任务详情 #${detail.value.id}`
})

const openDetail = async (r) => {
  if (!r?.id) return
  detailOpen.value = true
  detail.value = null
  try {
    const res = await temuAttrAiFillApi.getTask(r.id)
    if (res?.success) {
      detail.value = res.data
      return
    }
    message.error(res?.message || '加载详情失败')
  } catch (e) {
    message.error(e.message || '加载详情失败')
  }
}

const fmtTime = (t) => {
  if (!t) return ''
  return String(t).replace('T', ' ').replace('Z', '')
}

const pretty = (x) => {
  if (x == null) return ''
  try {
    if (typeof x === 'string') {
      const s = x.trim()
      if (!s) return ''
      if (s.startsWith('{') || s.startsWith('[')) {
        return JSON.stringify(JSON.parse(s), null, 2)
      }
      return x
    }
    return JSON.stringify(x, null, 2)
  } catch (e) {
    return String(x)
  }
}

onMounted(() => {
  reload()
})
</script>

<style scoped>
.page {
  padding: 16px;
}
.card {
  border-radius: 10px;
}
.head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.title {
  font-size: 16px;
  font-weight: 600;
}
.pager {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}
.pre {
  padding: 12px;
  background: #0b1020;
  color: #e6edf3;
  border-radius: 8px;
  overflow: auto;
  max-height: 520px;
  white-space: pre-wrap;
}
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}
</style>
