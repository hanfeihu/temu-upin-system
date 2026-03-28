<template>
  <ProLayout title="商品草稿库">
    <div class="page">
      <a-card class="toolbar" :bordered="false">
        <a-form layout="inline" :model="filters" @submit.prevent>
          <a-form-item label="关键词">
            <a-input v-model:value="filters.q" placeholder="商品名 / productId" style="width: 240px" allow-clear @pressEnter="reload" />
          </a-form-item>
          <a-form-item label="来源平台">
            <a-select v-model:value="filters.sourcePlatform" :options="platformOptions" allow-clear placeholder="全部" style="width: 160px" />
          </a-form-item>
          <a-form-item>
            <a-checkbox v-model:checked="filters.showDeleted">显示已删除</a-checkbox>
          </a-form-item>
          <a-form-item>
            <a-space>
              <a-button type="primary" @click="openImport">导入草稿</a-button>
              <a-button :loading="loading" @click="reload">查询</a-button>
              <a-button @click="reset">重置</a-button>
            </a-space>
          </a-form-item>
        </a-form>
      </a-card>

      <a-card :bordered="false">
        <a-table
          row-key="id"
          :columns="columns"
          :data-source="rows"
          :loading="loading"
          :pagination="pagination"
          :scroll="{ x: 1380 }"
          @change="onTableChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'productMainImage'">
              <a-image v-if="record.productMainImage" :src="record.productMainImage" :width="64" :height="64" style="object-fit: cover; border-radius: 8px" />
              <span v-else>-</span>
            </template>
            <template v-else-if="column.key === 'sourcePlatform'">
              <a-tag :color="record.sourcePlatform === 'TEMU' ? 'blue' : 'gold'">{{ record.sourcePlatform || '-' }}</a-tag>
            </template>
            <template v-else-if="column.key === 'productName'">
              <div class="name-cell">
                <div class="name-title" :title="record.productName || ''">{{ record.productName || '-' }}</div>
                <div class="name-sub mono">{{ record.productId || '-' }}</div>
              </div>
            </template>
            <template v-else-if="column.key === 'pushedToCollection'">
              <a-tag v-if="record.pushedToCollection" color="green">已推送</a-tag>
              <a-tag v-else color="default">未推送</a-tag>
            </template>
            <template v-else-if="column.key === 'actions'">
              <div class="action-group">
                <a-button type="link" size="small" class="action-button" @click="openDetail(record)">查看</a-button>
                <a-button type="link" size="small" class="action-button" @click="openEdit(record)">编辑</a-button>
                <a-button
                  type="link"
                  size="small"
                  class="action-button action-button-primary"
                  :loading="pushingDraftId === record.id"
                  :disabled="!!pushingDraftId && pushingDraftId !== record.id"
                  @click="pushDraft(record)"
                >
                  推送商品库
                </a-button>
                <a-button type="link" size="small" danger class="action-button" @click="confirmDelete(record)">删除</a-button>
              </div>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="importOpen"
        title="导入商品草稿"
        width="920"
        :confirm-loading="importing"
        @ok="submitImport"
      >
        <a-form layout="vertical">
          <a-form-item label="原始 HTML" required>
            <a-textarea v-model:value="importForm.html" :rows="14" placeholder="插件或手工采集的原始 html" />
          </a-form-item>
          <a-form-item label="提取补充 JSON（可选）">
            <a-textarea v-model:value="importForm.extractedJson" :rows="5" placeholder='例如：{"detailImages": [...]}' />
          </a-form-item>
        </a-form>
      </a-modal>

      <a-modal
        v-model:open="editOpen"
        title="编辑草稿"
        width="760"
        :confirm-loading="saving"
        @ok="saveEdit"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="商品名"><a-input v-model:value="editForm.productName" /></a-form-item>
          <a-form-item label="类目"><a-input v-model:value="editForm.productCategory" /></a-form-item>
          <a-form-item label="原始类目"><a-input v-model:value="editForm.originalCategory" /></a-form-item>
          <a-form-item label="主图 URL"><a-input v-model:value="editForm.productMainImage" /></a-form-item>
          <a-form-item label="商品 URL"><a-input v-model:value="editForm.productUrl" /></a-form-item>
          <a-form-item label="销量"><a-input v-model:value="editForm.monthlySales" /></a-form-item>
          <a-form-item label="评论数"><a-input-number v-model:value="editForm.reviewCount" :min="0" style="width: 100%" /></a-form-item>
          <a-form-item label="店铺/公司名"><a-input v-model:value="editForm.companyName" /></a-form-item>
        </a-form>
      </a-modal>

      <a-drawer v-model:open="detailOpen" title="草稿详情" width="920">
        <template v-if="detail">
          <a-descriptions :column="2" bordered size="small">
            <a-descriptions-item label="来源平台">{{ detail.sourcePlatform || '-' }}</a-descriptions-item>
            <a-descriptions-item label="productId">{{ detail.productId || '-' }}</a-descriptions-item>
            <a-descriptions-item label="商品名" :span="2">{{ detail.productName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="类目">{{ detail.productCategory || '-' }}</a-descriptions-item>
            <a-descriptions-item label="原始类目">{{ detail.originalCategory || '-' }}</a-descriptions-item>
            <a-descriptions-item label="销量">{{ detail.monthlySales || '-' }}</a-descriptions-item>
            <a-descriptions-item label="评论数">{{ detail.reviewCount ?? '-' }}</a-descriptions-item>
            <a-descriptions-item label="推送状态">{{ detail.pushedToCollection ? `已推送（${detail.pushedCollectionId || '-'}）` : '未推送' }}</a-descriptions-item>
            <a-descriptions-item label="推送备注">{{ detail.pushMessage || '-' }}</a-descriptions-item>
          </a-descriptions>

          <div class="detail-block">
            <div class="detail-label">原始 HTML</div>
            <a-textarea :value="detail.originalHtml || ''" :rows="12" readonly />
          </div>
          <div class="detail-block">
            <div class="detail-label">解析快照</div>
            <a-textarea :value="detail.parserSnapshotJson || ''" :rows="10" readonly />
          </div>
        </template>
      </a-drawer>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { productDraftApi } from '@/platform/api/productDrafts'

const loading = ref(false)
const importing = ref(false)
const saving = ref(false)
const pushingDraftId = ref(null)
const pendingPushDraftId = ref(null)
const rows = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = ref(20)

const importOpen = ref(false)
const editOpen = ref(false)
const detailOpen = ref(false)
const detail = ref(null)

const filters = reactive({
  q: '',
  sourcePlatform: undefined,
  showDeleted: false
})

const importForm = reactive({
  html: '',
  extractedJson: ''
})

const editForm = reactive({
  id: null,
  productName: '',
  productCategory: '',
  originalCategory: '',
  productMainImage: '',
  productUrl: '',
  monthlySales: '',
  reviewCount: null,
  companyName: ''
})

const platformOptions = [
  { label: 'TEMU', value: 'TEMU' },
  { label: '1688', value: '1688' }
]

const columns = [
  { title: '主图', key: 'productMainImage', width: 90, fixed: 'left' },
  { title: '商品', key: 'productName', width: 280, fixed: 'left' },
  { title: '来源平台', key: 'sourcePlatform', width: 110 },
  { title: '类目', dataIndex: 'productCategory', key: 'productCategory', width: 150 },
  { title: '原始类目', dataIndex: 'originalCategory', key: 'originalCategory', width: 240 },
  { title: '销量', dataIndex: 'monthlySales', key: 'monthlySales', width: 110 },
  { title: '评论数', dataIndex: 'reviewCount', key: 'reviewCount', width: 100 },
  { title: '推送状态', key: 'pushedToCollection', width: 110 },
  { title: '创建时间', dataIndex: 'createdAt', key: 'createdAt', width: 180 },
  { title: '操作', key: 'actions', width: 280, fixed: 'right' }
]

const pagination = reactive({
  current: page.value,
  pageSize: pageSize.value,
  total: total.value,
  showSizeChanger: true,
  showTotal: (t) => `共 ${t} 条`
})

const syncPagination = () => {
  pagination.current = page.value
  pagination.pageSize = pageSize.value
  pagination.total = total.value
}

const reload = async () => {
  loading.value = true
  try {
    const res = await productDraftApi.list({
      q: filters.q || undefined,
      sourcePlatform: filters.sourcePlatform || undefined,
      showDeleted: filters.showDeleted || undefined,
      page: page.value - 1,
      size: pageSize.value
    })
    if (!res?.success) throw new Error(res?.message || '加载失败')
    rows.value = Array.isArray(res.data?.content) ? res.data.content : []
    total.value = Number(res.data?.totalElements || 0)
    syncPagination()
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const reset = () => {
  filters.q = ''
  filters.sourcePlatform = undefined
  filters.showDeleted = false
  page.value = 1
  reload()
}

const onTableChange = (pager) => {
  page.value = pager.current || 1
  pageSize.value = pager.pageSize || 20
  reload()
}

const openImport = () => {
  importForm.html = ''
  importForm.extractedJson = ''
  importOpen.value = true
}

const submitImport = async () => {
  if (!(importForm.html || '').trim()) return message.error('请输入原始 HTML')
  importing.value = true
  try {
    const res = await productDraftApi.importDraft({
      html: importForm.html,
      extractedJson: importForm.extractedJson || undefined
    })
    if (!res?.success) throw new Error(res?.message || '导入失败')
    message.success('已导入草稿库')
    importOpen.value = false
    await reload()
  } catch (e) {
    message.error(e.message || '导入失败')
  } finally {
    importing.value = false
  }
}

const openEdit = (record) => {
  editForm.id = record?.id || null
  editForm.productName = record?.productName || ''
  editForm.productCategory = record?.productCategory || ''
  editForm.originalCategory = record?.originalCategory || ''
  editForm.productMainImage = record?.productMainImage || ''
  editForm.productUrl = record?.productUrl || ''
  editForm.monthlySales = record?.monthlySales || ''
  editForm.reviewCount = record?.reviewCount ?? null
  editForm.companyName = record?.companyName || ''
  editOpen.value = true
}

const saveEdit = async () => {
  if (!editForm.id) return
  saving.value = true
  try {
    const res = await productDraftApi.update(editForm.id, {
      productName: editForm.productName,
      productCategory: editForm.productCategory,
      originalCategory: editForm.originalCategory,
      productMainImage: editForm.productMainImage,
      productUrl: editForm.productUrl,
      monthlySales: editForm.monthlySales,
      reviewCount: editForm.reviewCount,
      companyName: editForm.companyName
    })
    if (!res?.success) throw new Error(res?.message || '保存失败')
    message.success('已保存')
    editOpen.value = false
    await reload()
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const openDetail = async (record) => {
  if (!record?.id) return
  try {
    const res = await productDraftApi.get(record.id)
    if (!res?.success) throw new Error(res?.message || '加载详情失败')
    detail.value = res.data || null
    detailOpen.value = true
  } catch (e) {
    message.error(e.message || '加载详情失败')
  }
}

const pushDraft = async (record) => {
  if (!record?.id || pushingDraftId.value || pendingPushDraftId.value) return
  pendingPushDraftId.value = record.id
  Modal.confirm({
    title: '推送商品库',
    content: `确认将“${record.productName || record.productId || '当前草稿'}”推送到商品库吗？`,
    okText: '确认推送',
    cancelText: '取消',
    okType: 'primary',
    onCancel() {
      pendingPushDraftId.value = null
    },
    async onOk() {
      if (pushingDraftId.value && pushingDraftId.value !== record.id) {
        pendingPushDraftId.value = null
        return Promise.reject(new Error('已有推送任务正在执行'))
      }
      pendingPushDraftId.value = null
      pushingDraftId.value = record.id
      try {
        const res = await productDraftApi.pushToCollection(record.id)
        if (!res?.success) throw new Error(res?.message || '推送失败')
        message.success(`已推送到采集商品库，ID=${res.data?.collectionId || '-'}`)
        await reload()
      } catch (e) {
        message.error(e.message || '推送失败')
        throw e
      } finally {
        pushingDraftId.value = null
      }
    }
  })
}

const confirmDelete = (record) => {
  if (!record?.id) return
  Modal.confirm({
    title: '删除草稿？',
    content: '删除后将从默认列表隐藏，但仍可在“显示已删除”中查看。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await productDraftApi.delete(record.id)
        if (!res?.success) throw new Error(res?.message || '删除失败')
        message.success('已删除')
        await reload()
      } catch (e) {
        message.error(e.message || '删除失败')
      }
    }
  })
}

onMounted(reload)
</script>

<style scoped>
.page {
  gap: 12px;
}

.name-cell {
  display: grid;
  gap: 4px;
}

.name-title {
  font-weight: 600;
}

.name-sub,
.mono {
  font-family: Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
  color: #666;
}

.action-group {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.action-button {
  padding-inline: 6px;
  height: 28px;
  border-radius: 6px;
}

.action-button-primary {
  font-weight: 600;
}

.detail-block {
  margin-top: 16px;
}

.detail-label {
  margin-bottom: 8px;
  font-weight: 600;
}
</style>
