<template>
  <ProLayout title="图片OCR任务">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <a-form layout="inline" :model="filters" @submit.prevent>
          <a-form-item label="SPU ID">
            <a-input
              v-model:value="filters.spuId"
              placeholder="例如 107"
              style="width: 160px"
              allow-clear
              @pressEnter="reload"
            />
          </a-form-item>
          <a-form-item label="产品ID">
            <a-input
              v-model:value="filters.productId"
              placeholder="模糊匹配"
              style="width: 200px"
              allow-clear
              @pressEnter="reload"
            />
          </a-form-item>
          <a-form-item label="图片类型">
            <a-select v-model:value="filters.imageType" style="width: 160px" allow-clear placeholder="全部">
              <a-select-option :value="1">轮播图</a-select-option>
              <a-select-option :value="2">详情图</a-select-option>
              <a-select-option :value="3">SKU图</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="执行状态">
            <a-select v-model:value="filters.execStatus" style="width: 160px" allow-clear placeholder="全部">
              <a-select-option :value="0">待接受</a-select-option>
              <a-select-option :value="1">执行中</a-select-option>
              <a-select-option :value="2">完成</a-select-option>
              <a-select-option :value="3">失败</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="图片过滤">
            <a-select v-model:value="filters.filtered" style="width: 160px" allow-clear placeholder="全部">
              <a-select-option :value="true">是</a-select-option>
              <a-select-option :value="false">否</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="包含中文">
            <a-select v-model:value="filters.containsChinese" style="width: 160px" allow-clear placeholder="全部">
              <a-select-option :value="true">是</a-select-option>
              <a-select-option :value="false">否</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item>
            <a-space>
              <a-button type="primary" :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
              <a-button type="primary" ghost @click="openCreate">新增任务</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card class="stats" :bordered="false">
        <div class="stats-grid">
          <div class="stat-tile">
            <div class="stat-title">总数</div>
            <div class="stat-value mono">{{ statTotal }}</div>
          </div>
          <div class="stat-tile stat-ok">
            <div class="stat-title">已完成</div>
            <div class="stat-value mono">{{ statDone }}</div>
          </div>
          <div class="stat-tile stat-bad">
            <div class="stat-title">失败</div>
            <div class="stat-value mono">{{ statFailed }}</div>
          </div>
          <div class="stat-tile stat-run">
            <div class="stat-title">执行中</div>
            <div class="stat-value mono">{{ statRunning }}</div>
          </div>
          <div class="stat-tile stat-pending">
            <div class="stat-title">待接受</div>
            <div class="stat-value mono">{{ statPending }}</div>
          </div>
        </div>
      </a-card>

      <a-card :bordered="false">
        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1400 }"
          size="middle"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'imageType'">
              <a-tag :color="typeColor(record.imageType)">{{ typeLabel(record.imageType) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'execStatus'">
              <a-tooltip v-if="record.execStatus === 3 && record.failReason" :title="record.failReason" placement="top">
                <a-tag :color="statusColor(record.execStatus)">{{ statusLabel(record.execStatus) }}</a-tag>
              </a-tooltip>
              <a-tag v-else :color="statusColor(record.execStatus)">{{ statusLabel(record.execStatus) }}</a-tag>
            </template>
            <template v-else-if="column.key === 'imageUrl'">
              <a :href="record.imageUrl" target="_blank" rel="noreferrer">打开图片</a>
            </template>
            <template v-else-if="column.key === 'filtered'">
              <a-tag :color="record.filtered ? 'red' : 'default'">{{ record.filtered ? '是' : '否' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'containsChinese'">
              <a-tag :color="record.containsChinese ? 'processing' : 'default'">{{ record.containsChinese ? '是' : '否' }}</a-tag>
            </template>
            <template v-else-if="['taskStartedAt', 'taskFinishedAt', 'updatedAt'].includes(column.key)">
              <span class="mono">{{ formatDateTime(record?.[column.key]) || '-' }}</span>
            </template>
            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openEdit(record)">编辑</a-button>
                <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑任务' : '新增任务'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="760"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="spuId" required>
            <a-input-number v-model:value="editForm.spuId" style="width: 240px" :min="1" />
          </a-form-item>
          <a-form-item label="productId">
            <a-input v-model:value="editForm.productId" placeholder="可选" />
          </a-form-item>
          <a-form-item label="图片类型" required>
            <a-select v-model:value="editForm.imageType" style="width: 240px">
              <a-select-option :value="1">轮播图</a-select-option>
              <a-select-option :value="2">详情图</a-select-option>
              <a-select-option :value="3">SKU图</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="图片URL" required>
            <a-input v-model:value="editForm.imageUrl" placeholder="http(s)://..." />
          </a-form-item>
          <a-form-item label="执行状态">
            <a-select v-model:value="editForm.execStatus" style="width: 240px" allow-clear placeholder="默认待接受">
              <a-select-option :value="0">待接受</a-select-option>
              <a-select-option :value="1">执行中</a-select-option>
              <a-select-option :value="2">完成</a-select-option>
              <a-select-option :value="3">失败</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="OCR内容">
            <a-textarea v-model:value="editForm.execResult" :rows="4" />
          </a-form-item>
          <a-form-item label="失败原因">
            <a-textarea v-model:value="editForm.failReason" :rows="2" />
          </a-form-item>
          <a-form-item label="执行者公网IP">
            <a-input v-model:value="editForm.executorPublicIp" />
          </a-form-item>
          <a-form-item label="是否过滤">
            <a-switch v-model:checked="editForm.filtered" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { ocrApi } from '@/platform/api/ocr'

const loading = ref(false)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const stats = reactive({
  total: 0,
  pending: 0,
  running: 0,
  success: 0,
  failed: 0
})

const statTotal = computed(() => stats.total || 0)
const statDone = computed(() => stats.success || 0)
const statFailed = computed(() => stats.failed || 0)
const statRunning = computed(() => stats.running || 0)
const statPending = computed(() => stats.pending || 0)

const fetchStats = async () => {
  try {
    const params = {
      spuId: filters.spuId ? Number(filters.spuId) : undefined,
      productId: filters.productId || undefined,
      imageType: filters.imageType ?? undefined,
      filtered: filters.filtered,
      containsChinese: filters.containsChinese
    }
    const res = await ocrApi.statsTasks(params)
    if (res?.success) {
      const d = res.data || {}
      stats.total = Number(d.total || 0)
      stats.pending = Number(d.pending || 0)
      stats.running = Number(d.running || 0)
      stats.success = Number(d.success || 0)
      stats.failed = Number(d.failed || 0)
      return
    }
  } catch (e) {
    // ignore stats errors
  }
}

const filters = reactive({
  spuId: '',
  productId: '',
  imageType: undefined,
  execStatus: undefined,
  filtered: undefined,
  containsChinese: undefined
})

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 90 },
  { title: 'spuId', dataIndex: 'spuId', key: 'spuId', width: 90 },
  { title: 'productId', dataIndex: 'productId', key: 'productId', width: 140 },
  { title: '类型', key: 'imageType', width: 110 },
  { title: '图片', key: 'imageUrl', width: 120 },
  { title: '状态', key: 'execStatus', width: 110 },
  { title: '已过滤', key: 'filtered', width: 90 },
  { title: '含中文', key: 'containsChinese', width: 90 },
  { title: '执行者IP', dataIndex: 'executorPublicIp', key: 'executorPublicIp', width: 140 },
  { title: '开始时间', dataIndex: 'taskStartedAt', key: 'taskStartedAt', width: 190 },
  { title: '完成时间', dataIndex: 'taskFinishedAt', key: 'taskFinishedAt', width: 190 },
  { title: '更新时间', dataIndex: 'updatedAt', key: 'updatedAt', width: 190 },
  { title: '操作', key: 'actions', width: 160, fixed: 'right' }
]

const statusLabel = (v) => {
  if (v === 0) return '待接受'
  if (v === 1) return '执行中'
  if (v === 2) return '完成'
  if (v === 3) return '失败'
  return String(v ?? '-')
}

const statusColor = (v) => {
  if (v === 0) return 'default'
  if (v === 1) return 'processing'
  if (v === 2) return 'green'
  if (v === 3) return 'red'
  return 'default'
}

const typeLabel = (v) => {
  if (v === 1) return '轮播图'
  if (v === 2) return '详情图'
  if (v === 3) return 'SKU图'
  return String(v ?? '-')
}

const typeColor = (v) => {
  if (v === 1) return 'blue'
  if (v === 2) return 'cyan'
  if (v === 3) return 'purple'
  return 'default'
}

const pad2 = (n) => String(n ?? '').padStart(2, '0')

const formatDateTime = (v) => {
  if (!v) return ''
  // Backend returns LocalDateTime as array: [yyyy, M, d, H, m, s, nanos]
  if (Array.isArray(v) && v.length >= 6) {
    const [yy, mo, dd, hh, mm, ss] = v
    if (!yy || !mo || !dd) return ''
    return `${yy}-${pad2(mo)}-${pad2(dd)} ${pad2(hh)}:${pad2(mm)}:${pad2(ss)}`
  }
  if (typeof v === 'string') return v
  try {
    const d = new Date(v)
    if (Number.isNaN(d.getTime())) return ''
    return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())} ${pad2(d.getHours())}:${pad2(d.getMinutes())}:${pad2(d.getSeconds())}`
  } catch {
    return ''
  }
}

const pagination = computed(() => ({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`,
  pageSizeOptions: ['10', '20', '50', '100']
}))

const fetchList = async () => {
  loading.value = true
  try {
    const params = {
      spuId: filters.spuId ? Number(filters.spuId) : undefined,
      productId: filters.productId || undefined,
      imageType: filters.imageType ?? undefined,
      execStatus: filters.execStatus ?? undefined,
      filtered: filters.filtered,
      containsChinese: filters.containsChinese,
      page: page.value - 1,
      size: pageSize.value
    }
    const res = await ocrApi.listTasks(params)
    if (res?.success) {
      rows.value = res.data?.content || []
      total.value = res.data?.totalElements || 0
      fetchStats()
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
  filters.productId = ''
  filters.imageType = undefined
  filters.execStatus = undefined
  filters.filtered = undefined
  filters.containsChinese = undefined
  await reload()
}

const onTableChange = (p) => {
  if (p?.current) page.value = p.current
  if (p?.pageSize) pageSize.value = p.pageSize
  fetchList()
}

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  spuId: null,
  productId: '',
  imageType: 1,
  imageUrl: '',
  execStatus: null,
  execResult: '',
  failReason: '',
  executorPublicIp: '',
  filtered: false
})

const openCreate = () => {
  editForm.id = null
  editForm.spuId = null
  editForm.productId = ''
  editForm.imageType = 1
  editForm.imageUrl = ''
  editForm.execStatus = null
  editForm.execResult = ''
  editForm.failReason = ''
  editForm.executorPublicIp = ''
  editForm.filtered = false
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.spuId = r?.spuId ?? null
  editForm.productId = r?.productId || ''
  editForm.imageType = r?.imageType ?? 1
  editForm.imageUrl = r?.imageUrl || ''
  editForm.execStatus = r?.execStatus ?? null
  editForm.execResult = r?.execResult || ''
  editForm.failReason = r?.failReason || ''
  editForm.executorPublicIp = r?.executorPublicIp || ''
  editForm.filtered = !!r?.filtered
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    spuId: editForm.spuId,
    productId: editForm.productId || null,
    imageType: editForm.imageType,
    imageUrl: String(editForm.imageUrl || '').trim(),
    execStatus: editForm.execStatus === null ? undefined : editForm.execStatus,
    execResult: editForm.execResult || null,
    failReason: editForm.failReason || null,
    executorPublicIp: editForm.executorPublicIp || null,
    filtered: !!editForm.filtered
  }
  if (!payload.spuId) {
    message.error('请输入 spuId')
    return
  }
  if (!payload.imageUrl) {
    message.error('请输入 图片URL')
    return
  }
  saving.value = true
  try {
    const res = editForm.id
      ? await ocrApi.updateTask(editForm.id, payload)
      : await ocrApi.createTask(payload)
    if (res?.success) {
      message.success('已保存')
      editOpen.value = false
      await fetchList()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const confirmDelete = (r) => {
  if (!r?.id) return
  Modal.confirm({
    title: '删除任务？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await ocrApi.deleteTask(r.id)
        if (res?.success) {
          message.success('已删除')
          await fetchList()
          return
        }
        message.error(res?.message || '删除失败')
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    }
  })
}

onMounted(() => {
  fetchList()
})
</script>

<style scoped>
.mono {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, 'Liberation Mono', 'Courier New', monospace;
}

.page {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.toolbar {
  border-radius: 14px;
}

.stats {
  border-radius: 14px;
  background:
    radial-gradient(900px 220px at 15% 0%, rgba(14, 165, 233, 0.10), transparent 60%),
    radial-gradient(700px 220px at 85% 0%, rgba(34, 197, 94, 0.10), transparent 58%),
    rgba(255, 255, 255, 0.72);
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(5, minmax(140px, 1fr));
  gap: 12px;
}

.stat-tile {
  padding: 12px 14px;
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.78);
  box-shadow: 0 8px 22px rgba(15, 23, 42, 0.05);
  min-width: 0;
}

.stat-title {
  font-size: 12px;
  color: rgba(15, 23, 42, 0.62);
  font-weight: 700;
}

.stat-value {
  margin-top: 6px;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.2px;
  color: rgba(15, 23, 42, 0.92);
}

.stat-ok {
  border-color: rgba(34, 197, 94, 0.22);
  background: rgba(34, 197, 94, 0.06);
}

.stat-bad {
  border-color: rgba(239, 68, 68, 0.24);
  background: rgba(239, 68, 68, 0.06);
}

.stat-run {
  border-color: rgba(59, 130, 246, 0.24);
  background: rgba(59, 130, 246, 0.06);
}

.stat-pending {
  border-color: rgba(100, 116, 139, 0.20);
  background: rgba(100, 116, 139, 0.06);
}

@media (max-width: 1200px) {
  .stats-grid {
    grid-template-columns: repeat(3, minmax(140px, 1fr));
  }
}

@media (max-width: 780px) {
  .stats-grid {
    grid-template-columns: repeat(2, minmax(140px, 1fr));
  }
}

/* Match product-collections: allow toolbar form to wrap on narrow screens */
.toolbar :deep(.ant-form-inline) {
  flex-wrap: wrap;
}

/* Keep page from horizontal scrolling; only table scrolls */
:deep(.ant-table-wrapper) {
  width: 100%;
  max-width: 100%;
}

:deep(.ant-table-content) {
  overflow-x: auto;
  overflow-y: hidden;
}

.toolbar {
  margin-bottom: 0;
}
</style>
