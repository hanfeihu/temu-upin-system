<template>
  <ProLayout title="AI 渠道管理">
    <div class="page">
      <a-card :bordered="false" class="card">
        <div class="head">
          <div class="title">渠道列表</div>
          <a-space>
            <a-input v-model:value="q" placeholder="搜索渠道/平台/模型" allow-clear style="width: 260px" @pressEnter="reload" />
            <a-select v-model:value="enabledFilter" allow-clear style="width: 120px" placeholder="状态">
              <a-select-option :value="true">启用</a-select-option>
              <a-select-option :value="false">禁用</a-select-option>
            </a-select>
            <a-button @click="reload" :loading="loading">刷新</a-button>
            <a-button type="primary" @click="openCreate">新增渠道</a-button>
          </a-space>
        </div>

        <a-table
          rowKey="id"
          :columns="columns"
          :dataSource="rows"
          :loading="loading"
          :pagination="false"
          :scroll="{ x: 1320 }"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'name'">
              <div class="name-wrap">
                <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
                <span>{{ record.name || '-' }}</span>
              </div>
            </template>

            <template v-else-if="column.key === 'credential'">
              <a-tag :color="credentialColor(record)">{{ credentialHint(record) }}</a-tag>
            </template>

            <template v-else-if="column.key === 'enabled'">
              <a-switch :checked="!!record.enabled" @change="(checked) => toggleEnabled(record, checked)" />
            </template>

            <template v-else-if="column.key === 'sort'">
              <a-input-number
                :value="record.sortOrder ?? 0"
                :min="0"
                :max="999"
                style="width: 110px"
                @change="(v) => updateSort(record, v)"
              />
            </template>

            <template v-else-if="column.key === 'actions'">
              <a-space>
                <a-button size="small" @click="openEdit(record)">编辑</a-button>
                <a-button size="small" @click="testChannel(record)" :loading="testingId === record.id">测试</a-button>
                <a-popconfirm title="确定删除该渠道？" ok-text="删除" cancel-text="取消" @confirm="removeChannel(record)">
                  <a-button size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>

      <a-modal
        v-model:open="editOpen"
        :title="editingId ? '编辑渠道' : '新增渠道'"
        :confirm-loading="saving"
        ok-text="保存"
        cancel-text="取消"
        @ok="save"
        width="760"
      >
        <a-form layout="vertical">
          <a-form-item label="渠道名称" required>
            <a-input v-model:value="form.name" placeholder="例如：稳定扩图渠道" />
          </a-form-item>

          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="平台" required>
                <a-input v-model:value="form.platform" placeholder="例如：stability / volcengine" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="模型" required>
                <a-input v-model:value="form.model" placeholder="例如：sd3" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="API Key">
                <a-input-password v-model:value="form.apiKey" placeholder="为空则不更新" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="API Secret">
                <a-input-password v-model:value="form.apiSecret" placeholder="为空则不更新" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item label="Base URL">
            <a-input v-model:value="form.baseUrl" placeholder="例如：https://api.stability.ai" />
          </a-form-item>

          <a-row :gutter="12">
            <a-col :span="12">
              <a-form-item label="排序">
                <a-input-number v-model:value="form.sortOrder" :min="0" :max="999" style="width: 100%" />
              </a-form-item>
            </a-col>
            <a-col :span="12">
              <a-form-item label="启用">
                <a-switch v-model:checked="form.enabled" />
              </a-form-item>
            </a-col>
          </a-row>

          <a-form-item label="描述">
            <a-textarea v-model:value="form.description" :rows="3" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { message } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { aiChannelsApi } from '@/platform/api/aiChannels'

const loading = ref(false)
const allRows = ref([])
const q = ref('')
const enabledFilter = ref(undefined)

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '渠道', key: 'name', width: 220 },
  { title: '平台', dataIndex: 'platform', key: 'platform', width: 160 },
  { title: '模型', dataIndex: 'model', key: 'model', width: 220 },
  { title: '凭证状态', key: 'credential', width: 120 },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '排序', key: 'sort', width: 130 },
  { title: 'Base URL', dataIndex: 'baseUrl', key: 'baseUrl', ellipsis: true },
  { title: '操作', key: 'actions', width: 220, fixed: 'right' }
]

const rows = computed(() => {
  let out = Array.isArray(allRows.value) ? [...allRows.value] : []
  const qq = (q.value || '').trim().toLowerCase()
  if (qq) {
    out = out.filter(r => `${r?.name || ''} ${r?.platform || ''} ${r?.model || ''}`.toLowerCase().includes(qq))
  }
  if (enabledFilter.value === true || enabledFilter.value === false) {
    out = out.filter(r => !!r?.enabled === enabledFilter.value)
  }
  out.sort((a, b) => {
    const sa = Number(a?.sortOrder ?? 0)
    const sb = Number(b?.sortOrder ?? 0)
    if (sa !== sb) return sa - sb
    return Number(a?.id ?? 0) - Number(b?.id ?? 0)
  })
  return out
})

const requiresSecret = (platform) => ['volcengine', 'jimeng_i2i', 'jimeng_t2i'].includes(String(platform || '').toLowerCase())
const isChannelOk = (ch) => {
  if (!ch) return false
  if (!ch.hasApiKey) return false
  if (requiresSecret(ch.platform) && !ch.hasApiSecret) return false
  return true
}
const credentialHint = (ch) => {
  if (!ch?.hasApiKey) return '缺少Key'
  if (requiresSecret(ch?.platform) && !ch?.hasApiSecret) return '缺少Secret'
  return '凭证OK'
}
const credentialColor = (ch) => (isChannelOk(ch) ? 'green' : 'red')

const reload = async () => {
  loading.value = true
  try {
    const res = await aiChannelsApi.listAll()
    if (res?.success) {
      allRows.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const toggleEnabled = async (row, checked) => {
  try {
    const res = await aiChannelsApi.update(row.id, { enabled: !!checked })
    if (res?.success) {
      row.enabled = !!checked
      message.success(checked ? '已启用' : '已禁用')
      return
    }
    message.error(res?.message || '更新失败')
  } catch (e) {
    message.error(e.message || '更新失败')
  }
}

const updateSort = async (row, val) => {
  const next = Number(val ?? 0)
  try {
    const res = await aiChannelsApi.update(row.id, { sortOrder: next })
    if (res?.success) {
      row.sortOrder = next
      return
    }
    message.error(res?.message || '更新失败')
  } catch (e) {
    message.error(e.message || '更新失败')
  }
}

const testingId = ref(null)
const testChannel = async (row) => {
  testingId.value = row.id
  try {
    const res = await aiChannelsApi.test(row.id)
    if (res?.success) {
      const ok = res.data?.ok
      message.success(ok ? '测试成功' : '测试未通过')
      return
    }
    message.error(res?.message || '测试失败')
  } catch (e) {
    message.error(e.message || '测试失败')
  } finally {
    testingId.value = null
  }
}

const editOpen = ref(false)
const saving = ref(false)
const editingId = ref(null)
const form = reactive({
  name: '',
  platform: '',
  model: '',
  apiKey: '',
  apiSecret: '',
  baseUrl: '',
  enabled: true,
  description: '',
  sortOrder: 0
})

const resetForm = () => {
  editingId.value = null
  form.name = ''
  form.platform = ''
  form.model = ''
  form.apiKey = ''
  form.apiSecret = ''
  form.baseUrl = ''
  form.enabled = true
  form.description = ''
  form.sortOrder = 0
}

const openCreate = () => {
  resetForm()
  editOpen.value = true
}

const openEdit = (row) => {
  editingId.value = row.id
  form.name = row.name || ''
  form.platform = row.platform || ''
  form.model = row.model || ''
  form.apiKey = ''
  form.apiSecret = ''
  form.baseUrl = row.baseUrl || ''
  form.enabled = !!row.enabled
  form.description = row.description || ''
  form.sortOrder = Number(row.sortOrder ?? 0)
  editOpen.value = true
}

const buildPayload = () => {
  const payload = {
    name: (form.name || '').trim(),
    platform: (form.platform || '').trim(),
    model: (form.model || '').trim(),
    baseUrl: (form.baseUrl || '').trim() || null,
    enabled: !!form.enabled,
    description: (form.description || '').trim() || null,
    sortOrder: Number(form.sortOrder ?? 0)
  }
  const key = (form.apiKey || '').trim()
  const secret = (form.apiSecret || '').trim()
  if (key) payload.apiKey = key
  if (secret) payload.apiSecret = secret
  return payload
}

const save = async () => {
  const name = (form.name || '').trim()
  const platform = (form.platform || '').trim()
  const model = (form.model || '').trim()
  if (!name || !platform || !model) {
    message.warning('渠道名称、平台、模型为必填')
    return
  }

  saving.value = true
  try {
    const payload = buildPayload()
    const res = editingId.value
      ? await aiChannelsApi.update(editingId.value, payload)
      : await aiChannelsApi.create(payload)
    if (res?.success) {
      message.success(editingId.value ? '更新成功' : '创建成功')
      editOpen.value = false
      await reload()
      return
    }
    message.error(res?.message || '保存失败')
  } catch (e) {
    message.error(e.message || '保存失败')
  } finally {
    saving.value = false
  }
}

const removeChannel = async (row) => {
  try {
    const res = await aiChannelsApi.remove(row.id)
    if (res?.success) {
      message.success('删除成功')
      await reload()
      return
    }
    message.error(res?.message || '删除失败')
  } catch (e) {
    message.error(e.message || '删除失败')
  }
}

onMounted(reload)
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
  font-weight: 700;
}
.name-wrap {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}
</style>
