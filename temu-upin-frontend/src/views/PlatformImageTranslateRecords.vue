<template>
  <ProLayout title="翻译记录">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <a-form layout="inline" :model="filters" @submit.prevent>
          <a-form-item label="SPU ID">
            <a-input v-model:value="filters.spuId" placeholder="商品 ID" allow-clear style="width: 180px" @pressEnter="reload" />
          </a-form-item>
          <a-form-item label="翻译通道">
            <a-select v-model:value="filters.provider" allow-clear style="width: 160px" placeholder="全部">
              <a-select-option value="aliyun">aliyun</a-select-option>
              <a-select-option value="temu">temu</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="状态">
            <a-select v-model:value="filters.status" allow-clear style="width: 140px" placeholder="全部">
              <a-select-option value="success">success</a-select-option>
              <a-select-option value="failed">failed</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="原图 URL">
            <a-input v-model:value="filters.sourceUrl" placeholder="支持模糊匹配" allow-clear style="width: 320px" @pressEnter="reload" />
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="table-card" :bordered="false">
        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1500 }"
          tableLayout="fixed"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'sourceUrl' || column.key === 'translatedUrl'">
              <a v-if="record?.[column.key]" :href="record[column.key]" target="_blank" rel="noreferrer noopener" class="url-cell">
                {{ record[column.key] }}
              </a>
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'fieldRefs'">
              <div class="content-cell">{{ parseFieldRefs(record.fieldRefs) }}</div>
            </template>
            <template v-else-if="column.key === 'errorMsg'">
              <div class="content-cell error">{{ record.errorMsg || '-' }}</div>
            </template>
            <template v-else-if="column.key === 'status'">
              <a-tag :color="record.status === 'success' ? 'green' : 'red'">{{ record.status || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'startedAt' || column.key === 'endedAt'">
              <span class="mono">{{ formatDateTime(record?.[column.key]) || '-' }}</span>
            </template>
          </template>
        </a-table>
      </a-card>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { imageTranslateRecordsApi } from '@/platform/api/imageTranslateRecords'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const filters = reactive({
  spuId: '',
  provider: undefined,
  status: undefined,
  sourceUrl: ''
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'SPU ID', dataIndex: 'spuId', key: 'spuId', width: 100 },
  { title: '通道', dataIndex: 'provider', key: 'provider', width: 110 },
  { title: '状态', dataIndex: 'status', key: 'status', width: 110 },
  { title: '命中字段', dataIndex: 'fieldRefs', key: 'fieldRefs', width: 260 },
  { title: '原图 URL', dataIndex: 'sourceUrl', key: 'sourceUrl', width: 300 },
  { title: '译图 URL', dataIndex: 'translatedUrl', key: 'translatedUrl', width: 300 },
  { title: '开始时间', dataIndex: 'startedAt', key: 'startedAt', width: 190 },
  { title: '结束时间', dataIndex: 'endedAt', key: 'endedAt', width: 190 },
  { title: '错误信息', dataIndex: 'errorMsg', key: 'errorMsg', width: 220 }
]

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const pad2 = (n) => String(n ?? '').padStart(2, '0')
const formatDateTime = (v) => {
  if (!v) return ''
  if (Array.isArray(v) && v.length >= 6) {
    const [yy, mo, dd, hh, mm, ss] = v
    if (!yy || !mo || !dd) return ''
    return `${yy}-${pad2(mo)}-${pad2(dd)} ${pad2(hh)}:${pad2(mm)}:${pad2(ss)}`
  }
  if (typeof v === 'string') return v
  return ''
}

const parseFieldRefs = (value) => {
  if (!value) return '-'
  try {
    const parsed = JSON.parse(value)
    if (Array.isArray(parsed)) {
      return parsed.join(' , ')
    }
  } catch {
  }
  return String(value)
}

const fetchList = async () => {
  loading.value = true
  try {
    const parsedSpuId = String(filters.spuId || '').trim()
    const res = await imageTranslateRecordsApi.list({
      spuId: parsedSpuId ? Number(parsedSpuId) : undefined,
      provider: filters.provider || undefined,
      status: filters.status || undefined,
      sourceUrl: filters.sourceUrl || undefined,
      page: page.value - 1,
      size: pageSize.value
    })
    if (res?.success) {
      rows.value = res.data?.content || []
      total.value = res.data?.totalElements || 0
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const reload = async () => {
  page.value = 1
  await fetchList()
}

const reset = async () => {
  filters.spuId = ''
  filters.provider = undefined
  filters.status = undefined
  filters.sourceUrl = ''
  await reload()
}

const onTableChange = (p) => {
  page.value = p.current
  pageSize.value = p.pageSize
  fetchList()
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar,
.table-card {
  border-radius: 14px;
}

.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.content-cell,
.url-cell {
  display: block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.error {
  color: #b91c1c;
}
</style>