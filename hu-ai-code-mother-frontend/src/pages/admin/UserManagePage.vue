<template>
  <div id="userManagePage">
    <!-- 搜索表单 -->
    <a-form layout="inline" :model="searchParams" @finish="doSearch">
      <a-form-item label="账号">
        <a-input v-model:value="searchParams.userAccount" placeholder="输入账号" />
      </a-form-item>
      <a-form-item label="用户名">
        <a-input v-model:value="searchParams.userName" placeholder="输入用户名" />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" html-type="submit">搜索</a-button>
      </a-form-item>
    </a-form>
    <a-divider />
    <!-- 表格 -->
    <a-table
      :columns="columns"
      :data-source="data"
      :pagination="pagination"
      @change="doTableChange"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.dataIndex === 'userAvatar'">
          <a-image :src="record.userAvatar" :width="120" />
        </template>
        <template v-else-if="column.dataIndex === 'userRole'">
          <div v-if="record.userRole === 'admin'">
            <a-tag color="green">管理员</a-tag>
          </div>
          <div v-else>
            <a-tag color="blue">普通用户</a-tag>
          </div>
        </template>
        <template v-else-if="column.dataIndex === 'createTime'">
          {{ dayjs(record.createTime).format('YYYY-MM-DD HH:mm:ss') }}
        </template>
        <template v-else-if="column.key === 'action'">
          <a-space>
            <a-button @click="openQuotaModal(record)" data-testid="user-quota-open">应用额度</a-button>
            <a-popconfirm title="确定删除这个用户吗？" ok-text="删除" cancel-text="取消" @confirm="doDelete(record.id)">
              <a-button danger :loading="deletingIds.has(String(record.id))">删除</a-button>
            </a-popconfirm>
          </a-space>
        </template>
      </template>
    </a-table>
    <a-modal
      v-model:open="quotaModalOpen"
      title="设置用户应用额度"
      :confirm-loading="quotaSaving"
      :ok-button-props="{ disabled: quotaLoading || !!quotaError || !selectedQuota || !quotaInputValid }"
      :cancel-button-props="{ disabled: quotaSaving }"
      :closable="!quotaSaving"
      :mask-closable="!quotaSaving"
      :keyboard="!quotaSaving"
      ok-text="保存"
      cancel-text="取消"
      @ok="saveUserQuota"
      @cancel="closeQuotaModal"
    >
      <p><strong>{{ selectedUser?.userName || selectedUser?.userAccount || '未命名用户' }}</strong>（ID：{{ selectedUser?.id }}）</p>
      <a-spin :spinning="quotaLoading">
        <a-alert v-if="quotaError" type="error" show-icon :message="quotaError" class="quota-notice">
          <template #action><a-button size="small" :loading="quotaLoading" @click="loadUserQuota">重试</a-button></template>
        </a-alert>
        <template v-if="selectedQuota && !quotaError">
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="已使用">{{ selectedQuota.usedCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="当前上限">{{ selectedQuota.limitCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="剩余">{{ selectedQuota.remainingCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="系统默认">{{ selectedQuota.defaultLimit }} 个应用</a-descriptions-item>
            <a-descriptions-item label="当前来源">{{ selectedQuota.usesDefault ? '继承系统默认' : '单独设置' }}</a-descriptions-item>
          </a-descriptions>
          <a-form layout="vertical" class="quota-form">
            <a-form-item>
              <a-checkbox v-model:checked="inheritDefault" :disabled="quotaSaving" data-testid="quota-inherit">继承系统默认额度（{{ selectedQuota.defaultLimit }} 个）</a-checkbox>
            </a-form-item>
            <a-form-item label="单独设置上限" extra="允许 0～10000 的整数。0 表示不允许创建新应用，与继承默认额度不同。" :validate-status="!inheritDefault && !quotaInputValid ? 'error' : undefined">
              <a-input-number v-model:value="appLimit" :min="0" :max="10000" :precision="0" :disabled="inheritDefault || quotaSaving" style="width: 100%" data-testid="quota-limit" />
            </a-form-item>
          </a-form>
          <a-alert
            v-if="proposedLimit != null && proposedLimit < (selectedQuota.usedCount ?? 0)"
            type="warning"
            show-icon
            message="新上限低于当前已使用数量"
            description="已有作品不会自动删除；该用户在应用数量降至上限以下之前无法创建新应用。"
            class="quota-notice"
          />
        </template>
      </a-spin>
    </a-modal>
  </div>
</template>
<script lang="ts" setup>
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue'
import { deleteUser, getUserQuota, listUserVoByPage, updateUserQuota } from '@/api/userController.ts'
import { useLoginUserStore } from '@/stores/loginUser'
import { useAppQuotaStore } from '@/stores/appQuota'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import { toSafeCount } from '@/utils/count'

const loginUserStore = useLoginUserStore()
const quotaStore = useAppQuotaStore()
let disposed = false
let quotaSequence = 0
let quotaSaveSequence = 0
const quotaModalOpen = ref(false)
const selectedUser = ref<API.UserVO>()
const selectedQuota = ref<API.AppQuotaVO | null>(null)
const quotaLoading = ref(false)
const quotaSaving = ref(false)
const quotaError = ref('')
const inheritDefault = ref(true)
const appLimit = ref<number | null>(null)
const quotaInputValid = computed(() => inheritDefault.value || (
  appLimit.value != null && Number.isInteger(appLimit.value) && appLimit.value >= 0 && appLimit.value <= 10000
))
const proposedLimit = computed(() => inheritDefault.value ? selectedQuota.value?.defaultLimit : appLimit.value)
const quotaTargetCurrent = (id: string, version: number) =>
  !disposed && quotaModalOpen.value && String(selectedUser.value?.id ?? '') === id && loginUserStore.authVersion === version

const invalidateQuotaModal = () => {
  quotaSequence += 1
  quotaSaveSequence += 1
  quotaModalOpen.value = false
  selectedUser.value = undefined
  selectedQuota.value = null
  quotaLoading.value = false
  quotaSaving.value = false
  quotaError.value = ''
  inheritDefault.value = true
  appLimit.value = null
}
const closeQuotaModal = () => { if (!quotaSaving.value) invalidateQuotaModal() }
const loadUserQuota = async () => {
  if (selectedUser.value?.id == null || !quotaModalOpen.value) return
  const id = String(selectedUser.value.id)
  const version = loginUserStore.authVersion
  const sequence = ++quotaSequence
  quotaLoading.value = true
  quotaError.value = ''
  selectedQuota.value = null
  try {
    const res = await getUserQuota({ id })
    if (!quotaTargetCurrent(id, version) || sequence !== quotaSequence) return
    if (res.data.code === 0 && res.data.data) {
      selectedQuota.value = res.data.data
      inheritDefault.value = !!res.data.data.usesDefault
      appLimit.value = res.data.data.appLimit ?? res.data.data.limitCount ?? null
    } else {
      quotaError.value = res.data.message || '额度加载失败，请重试'
    }
  } catch {
    if (quotaTargetCurrent(id, version) && sequence === quotaSequence) quotaError.value = '额度加载失败，请检查网络后重试'
  } finally {
    if (quotaTargetCurrent(id, version) && sequence === quotaSequence) quotaLoading.value = false
  }
}
const openQuotaModal = (user: API.UserVO) => {
  if (user.id == null || quotaSaving.value) return
  invalidateQuotaModal()
  selectedUser.value = user
  quotaModalOpen.value = true
  void loadUserQuota()
}
const saveUserQuota = async () => {
  if (!quotaInputValid.value || !selectedQuota.value || quotaLoading.value || quotaSaving.value || selectedUser.value?.id == null) return
  const id = String(selectedUser.value.id)
  const version = loginUserStore.authVersion
  const sequence = ++quotaSaveSequence
  quotaSaving.value = true
  try {
    const res = await updateUserQuota({ id, appLimit: inheritDefault.value ? null : appLimit.value })
    if (!quotaTargetCurrent(id, version) || sequence !== quotaSaveSequence) return
    if (res.data.code === 0) {
      message.success('用户应用额度已更新')
      invalidateQuotaModal()
      if (String(loginUserStore.loginUser.id ?? '') === id) void quotaStore.fetchQuota(true)
    } else {
      message.error(res.data.message || '额度保存失败，请重试')
    }
  } catch {
    if (quotaTargetCurrent(id, version) && sequence === quotaSaveSequence) message.error('额度保存失败，请检查网络后重试')
  } finally {
    if (quotaTargetCurrent(id, version) && sequence === quotaSaveSequence) quotaSaving.value = false
  }
}
watch(() => loginUserStore.authVersion, invalidateQuotaModal, { flush: 'sync' })
onUnmounted(() => {
  disposed = true
  invalidateQuotaModal()
})

const columns = [
  {
    title: 'id',
    dataIndex: 'id',
  },
  {
    title: '账号',
    dataIndex: 'userAccount',
  },
  {
    title: '用户名',
    dataIndex: 'userName',
  },
  {
    title: '头像',
    dataIndex: 'userAvatar',
  },
  {
    title: '简介',
    dataIndex: 'userProfile',
  },
  {
    title: '用户角色',
    dataIndex: 'userRole',
  },
  {
    title: '创建时间',
    dataIndex: 'createTime',
  },
  {
    title: '操作',
    key: 'action',
  },
]

// 展示的数据
const data = ref<API.UserVO[]>([])
const total = ref(0)

// 搜索条件
const searchParams = reactive<API.UserQueryRequest>({
  pageNum: 1,
  pageSize: 10,
})

// 获取数据
const fetchData = async () => {
  try {
    const res = await listUserVoByPage({ ...searchParams })
    if (disposed) return
    if (res.data.code === 0 && res.data.data) {
      total.value = toSafeCount(res.data.data.totalRow)
      data.value = res.data.data.records ?? []
    } else {
      message.error('获取数据失败，' + res.data.message)
    }
  } catch {
    if (!disposed) message.error('获取数据失败，请重试')
  }
}

// 分页参数
const pagination = computed(() => {
  return {
    current: toSafeCount(searchParams.pageNum ?? 1),
    pageSize: toSafeCount(searchParams.pageSize ?? 10),
    total: total.value,
    showSizeChanger: true,
    showTotal: (total: number) => `共 ${total} 条`,
  }
})

// 表格分页变化时的操作
const doTableChange = (page: { current: number; pageSize: number }) => {
  searchParams.pageNum = page.current
  searchParams.pageSize = page.pageSize
  fetchData()
}

// 搜索数据
const doSearch = () => {
  // 重置页码
  searchParams.pageNum = 1
  fetchData()
}

// 删除数据
const deletingIds = ref(new Set<string>())
const doDelete = async (id: string | number | undefined) => {
  if (id == null) return
  const targetId = String(id)
  if (deletingIds.value.has(targetId)) return
  const version = loginUserStore.authVersion
  deletingIds.value.add(targetId)
  try {
    const res = await deleteUser({ id: targetId })
    if (disposed || version !== loginUserStore.authVersion) return
    if (res.data.code === 0) {
      message.success('删除成功')
      if (String(selectedUser.value?.id ?? '') === targetId) invalidateQuotaModal()
      void fetchData()
    } else {
      message.error(res.data.message || '删除失败')
    }
  } catch {
    if (!disposed && version === loginUserStore.authVersion) message.error('删除失败，请重试')
  } finally {
    deletingIds.value.delete(targetId)
  }
}

// 页面加载时请求一次
onMounted(() => {
  fetchData()
})
</script>

<style scoped>
#userManagePage {
  padding: 24px;
  background: white;
  margin-top: 16px;
}
.quota-form { margin-top: 20px; }
.quota-notice { margin: 16px 0; }
</style>
