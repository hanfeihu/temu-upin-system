<template>
  <ProLayout title="主销售属性规则">
    <div class="page">
      <div class="toolbar">
        <a-space>
          <a-button type="primary" @click="openCreate">新增规则</a-button>
          <a-button @click="reload" :loading="loading">刷新</a-button>
        </a-space>
      </div>

      <a-table
        :columns="columns"
        :data-source="rows"
        :loading="loading"
        row-key="id"
        :pagination="false"
        bordered
        size="middle"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'ruleType'">
            <a-tag :color="record.ruleType === 'MANUAL' ? 'blue' : 'gold'">
              {{ record.ruleType === 'MANUAL' ? '手动规则' : '成功学习' }}
            </a-tag>
          </template>

          <template v-else-if="column.key === 'enabled'">
            <a-tag :color="record.enabled ? 'green' : 'default'">{{ record.enabled ? '启用' : '禁用' }}</a-tag>
          </template>

          <template v-else-if="column.key === 'actions'">
            <a-space>
              <a-button size="small" @click="openEdit(record)">编辑</a-button>
              <a-button size="small" danger @click="confirmDelete(record)">删除</a-button>
            </a-space>
          </template>
        </template>
      </a-table>

      <a-modal
        v-model:open="editOpen"
        :title="editForm.id ? '编辑规则' : '新增规则'"
        :confirm-loading="saving"
        @ok="save"
        @cancel="closeEdit"
        width="820"
        :bodyStyle="{ maxHeight: '70vh', overflowY: 'auto', overflowX: 'hidden' }"
      >
        <a-form layout="vertical" :model="editForm">
          <a-form-item label="规则类型" required>
            <a-radio-group v-model:value="editForm.ruleType">
              <a-radio value="MANUAL">手动规则</a-radio>
              <a-radio value="LEARNED">成功学习（不建议手动创建）</a-radio>
            </a-radio-group>
          </a-form-item>

          <a-form-item label="SKU 签名（specJson 完全匹配）" required>
            <a-textarea v-model:value="editForm.skuSignature" :rows="4" placeholder="由系统生成；用于完全匹配，建议复制粘贴，不要手改" />
          </a-form-item>

          <a-form-item label="主销售属性（parentSpecName）" required>
            <a-select v-model:value="editForm.parentSpecName" style="width: 260px">
              <a-select-option value="颜色">颜色</a-select-option>
              <a-select-option value="尺码">尺码</a-select-option>
              <a-select-option value="型号">型号</a-select-option>
              <a-select-option value="规格">规格</a-select-option>
              <a-select-option value="数量">数量</a-select-option>
            </a-select>
          </a-form-item>

          <a-form-item label="维度 key（dimKey，可选）">
            <a-input v-model:value="editForm.dimKey" placeholder="例如 颜色 / Size / 型号" />
          </a-form-item>

          <a-form-item label="启用">
            <a-switch v-model:checked="editForm.enabled" />
          </a-form-item>
        </a-form>
      </a-modal>
    </div>
  </ProLayout>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { message, Modal } from 'ant-design-vue'
import ProLayout from '@/platform/components/ProLayout.vue'
import { mainSaleSpecRuleApi } from '@/platform/api/mainSaleSpecRules'

const loading = ref(false)
const rows = ref([])

const editOpen = ref(false)
const saving = ref(false)
const editForm = reactive({
  id: null,
  ruleType: 'MANUAL',
  skuSignature: '',
  parentSpecName: '型号',
  dimKey: '',
  enabled: true
})

const columns = [
  { title: '类型', key: 'ruleType', width: 120 },
  { title: '主销售属性', dataIndex: 'parentSpecName', key: 'parentSpecName', width: 120 },
  { title: 'dimKey', dataIndex: 'dimKey', key: 'dimKey', width: 160 },
  { title: 'SKU签名', dataIndex: 'skuSignature', key: 'skuSignature' },
  { title: '启用', key: 'enabled', width: 90 },
  { title: '操作', key: 'actions', width: 160 }
]

const reload = async () => {
  loading.value = true
  try {
    const res = await mainSaleSpecRuleApi.list({ enabled: true })
    if (res?.success) {
      rows.value = Array.isArray(res.data) ? res.data : []
      return
    }
    message.error(res?.message || '加载失败')
  } catch (e) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
}

const openCreate = () => {
  editForm.id = null
  editForm.ruleType = 'MANUAL'
  editForm.skuSignature = ''
  editForm.parentSpecName = '型号'
  editForm.dimKey = ''
  editForm.enabled = true
  editOpen.value = true
}

const openEdit = (r) => {
  editForm.id = r?.id ?? null
  editForm.ruleType = r?.ruleType || 'MANUAL'
  editForm.skuSignature = r?.skuSignature || ''
  editForm.parentSpecName = r?.parentSpecName || '型号'
  editForm.dimKey = r?.dimKey || ''
  editForm.enabled = !!r?.enabled
  editOpen.value = true
}

const closeEdit = () => {
  editOpen.value = false
}

const save = async () => {
  const payload = {
    ruleType: (editForm.ruleType || '').trim(),
    skuSignature: (editForm.skuSignature || '').trim(),
    parentSpecName: (editForm.parentSpecName || '').trim(),
    dimKey: (editForm.dimKey || '').trim() || null,
    enabled: !!editForm.enabled
  }

  if (!payload.ruleType) return message.error('请选择规则类型')
  if (!payload.skuSignature) return message.error('请输入 SKU 签名')
  if (!payload.parentSpecName) return message.error('请选择主销售属性')

  saving.value = true
  try {
    let res
    if (editForm.id) {
      res = await mainSaleSpecRuleApi.update(editForm.id, payload)
    } else {
      res = await mainSaleSpecRuleApi.create(payload)
    }
    if (res?.success) {
      message.success('已保存')
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

const confirmDelete = (r) => {
  if (!r?.id) return
  Modal.confirm({
    title: '删除规则？',
    content: '删除后不可恢复。',
    okText: '删除',
    okType: 'danger',
    cancelText: '取消',
    onOk: async () => {
      try {
        const res = await mainSaleSpecRuleApi.delete(r.id)
        if (res?.success) {
          message.success('已删除')
          await reload()
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
  reload()
})
</script>

<style scoped>
.page {
  display: grid;
  gap: 12px;
}

.toolbar {
  display: flex;
  justify-content: space-between;
}
</style>
