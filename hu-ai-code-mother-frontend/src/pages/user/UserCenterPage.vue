<script setup lang="ts">
import { computed, onUnmounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { message, type FormInstance } from 'ant-design-vue'
import type { Rule } from 'ant-design-vue/es/form'
import { UserOutlined } from '@ant-design/icons-vue'
import { useLoginUserStore } from '@/stores/loginUser'
import { useAppQuotaStore } from '@/stores/appQuota'
import { updateMyUser } from '@/api/userController'
import { deleteApp, listMyAppVoByPage } from '@/api/appController'
import AppCard from '@/components/AppCard.vue'
import AppDetailModal from '@/components/AppDetailModal.vue'
import { getDeployUrl } from '@/config/env'
import { formatTime } from '@/utils/time'
import { toSafeCount } from '@/utils/count'

const route = useRoute()
const router = useRouter()
const loginUserStore = useLoginUserStore()
const quotaStore = useAppQuotaStore()
const userId = computed(() => String(loginUserStore.loginUser.id ?? ''))
const activeTab = computed(() => {
  const tab = route.query.tab
  return tab === 'works' || tab === 'quota' ? tab : 'profile'
})
const changeTab = (tab: string | number) => {
  void router.replace({ query: { ...route.query, tab: String(tab) } })
}

let disposed = false
let profileSequence = 0
let worksSequence = 0
let deleteSequence = 0
const isCurrentIdentity = (id: string, version: number) =>
  !disposed && id === userId.value && version === loginUserStore.authVersion

const profileForm = ref<FormInstance>()
const profile = reactive({ userName: '', userAvatar: '', userProfile: '' })
const savedProfile = reactive({ userName: '', userAvatar: '', userProfile: '' })
const saving = ref(false)
const avatarFailed = ref(false)
const profileDirty = computed(() =>
  (Object.keys(profile) as (keyof typeof profile)[]).some(
    (key) => profile[key] !== savedProfile[key],
  ),
)
const validAvatarUrl = (value: string) => {
  if (!value) return true
  if (value.length > 1024) return false
  try {
    const url = new URL(value)
    return url.protocol === 'https:' && !!url.hostname && !url.username && !url.password
  } catch {
    return false
  }
}
const avatarPreview = computed(() => {
  const url = profile.userAvatar.trim()
  return validAvatarUrl(url) && !avatarFailed.value ? url : undefined
})
const rules: Record<string, Rule[]> = {
  userName: [
    {
      validator: async (_rule, value: string) => {
        const length = (value ?? '').trim().length
        if (length < 2 || length > 20) throw new Error('昵称去除首尾空格后须为 2～20 个字符')
      },
      trigger: 'blur',
    },
  ],
  userProfile: [{ max: 200, message: '简介不能超过 200 个字符', trigger: 'change' }],
  userAvatar: [
    {
      validator: async (_rule, value: string) => {
        if (!validAvatarUrl((value ?? '').trim())) {
          throw new Error('请输入不超过 1024 个字符的 HTTPS 头像链接，或留空')
        }
      },
      trigger: 'blur',
    },
  ],
}

const resetProfile = () => {
  const user = loginUserStore.loginUser
  Object.assign(savedProfile, {
    userName: userId.value ? user.userName ?? '' : '',
    userAvatar: userId.value ? user.userAvatar ?? '' : '',
    userProfile: userId.value ? user.userProfile ?? '' : '',
  })
  Object.assign(profile, savedProfile)
  profileForm.value?.clearValidate()
  avatarFailed.value = false
}

const saveProfile = async () => {
  if (!userId.value || saving.value) return
  const id = userId.value
  const version = loginUserStore.authVersion
  const sequence = ++profileSequence
  const body: API.UserUpdateMyRequest = {
    userName: profile.userName.trim(),
    userAvatar: profile.userAvatar.trim(),
    userProfile: profile.userProfile,
  }
  saving.value = true
  try {
    const res = await updateMyUser(body)
    if (!isCurrentIdentity(id, version) || sequence !== profileSequence) return
    if (res.data.code === 0 && res.data.data && String(res.data.data.id ?? '') === id) {
      loginUserStore.setLoginUser(res.data.data)
      resetProfile()
      message.success('个人资料已保存')
    } else {
      message.error(res.data.message || '保存失败，请重试')
    }
  } catch {
    if (isCurrentIdentity(id, version) && sequence === profileSequence) {
      message.error('保存失败，请检查网络后重试；已保留填写内容')
    }
  } finally {
    if (isCurrentIdentity(id, version) && sequence === profileSequence) saving.value = false
  }
}

watch(() => profile.userAvatar, () => { avatarFailed.value = false })
watch(() => loginUserStore.loginUser, () => {
  if (!saving.value && !profileDirty.value) resetProfile()
})

const apps = ref<API.AppVO[]>([])
const worksLoading = ref(false)
const worksError = ref('')
const appName = ref('')
const appliedName = ref('')
const worksPage = reactive({ current: 1, pageSize: 8, total: 0 })
const selectedApp = ref<API.AppVO>()
const detailOpen = ref(false)
const deleting = ref(false)
const ownsApp = (app?: API.AppVO) => {
  const owner = app?.userId ?? app?.user?.id
  return !!userId.value && owner != null && String(owner) === userId.value
}
const selectedOwned = computed(() => ownsApp(selectedApp.value))

const loadWorks = async () => {
  if (!userId.value || disposed) return
  const id = userId.value
  const version = loginUserStore.authVersion
  const sequence = ++worksSequence
  worksLoading.value = true
  worksError.value = ''
  try {
    const res = await listMyAppVoByPage({
      appName: appliedName.value || undefined,
      pageNum: worksPage.current,
      pageSize: Math.min(worksPage.pageSize, 20),
      sortField: 'createTime',
      sortOrder: 'desc',
    })
    if (!isCurrentIdentity(id, version) || sequence !== worksSequence) return
    if (res.data.code === 0 && res.data.data) {
      apps.value = res.data.data.records ?? []
      worksPage.total = toSafeCount(res.data.data.totalRow)
    } else {
      worksError.value = res.data.message || '作品加载失败，请重试'
    }
  } catch {
    if (isCurrentIdentity(id, version) && sequence === worksSequence) {
      worksError.value = '作品加载失败，请检查网络后重试'
    }
  } finally {
    if (isCurrentIdentity(id, version) && sequence === worksSequence) worksLoading.value = false
  }
}
const searchWorks = () => {
  appliedName.value = appName.value.trim()
  worksPage.current = 1
  void loadWorks()
}
const changeWorksPage = (page: number, pageSize: number) => {
  worksPage.current = pageSize === worksPage.pageSize ? page : 1
  worksPage.pageSize = Math.min(pageSize, 20)
  void loadWorks()
}
const viewChat = (id: string | number | undefined) => {
  if (id != null) void router.push(`/app/chat/${id}?view=1`)
}
const continueCreating = (app: API.AppVO) => {
  if (app.id != null && ownsApp(app)) void router.push(`/app/chat/${app.id}`)
}
const viewWork = (app: API.AppVO) => {
  if (app.deployKey) window.open(getDeployUrl(app.deployKey), '_blank', 'noopener,noreferrer')
}
const showDetail = (app: API.AppVO) => {
  if (!ownsApp(app) || deleting.value) return
  selectedApp.value = app
  detailOpen.value = true
}
const editSelectedApp = () => {
  if (selectedOwned.value && selectedApp.value?.id != null && !deleting.value) {
    void router.push(`/app/edit/${selectedApp.value.id}`)
  }
}
const deleteSelectedApp = async () => {
  if (!selectedOwned.value || selectedApp.value?.id == null || deleting.value) return
  const appId = String(selectedApp.value.id)
  const id = userId.value
  const version = loginUserStore.authVersion
  const sequence = ++deleteSequence
  deleting.value = true
  try {
    const res = await deleteApp({ id: appId })
    if (!isCurrentIdentity(id, version) || sequence !== deleteSequence) return
    if (res.data.code === 0) {
      message.success('应用已删除，额度将重新统计')
      detailOpen.value = false
      selectedApp.value = undefined
      if (apps.value.length === 1 && worksPage.current > 1) worksPage.current -= 1
      await Promise.all([loadWorks(), quotaStore.fetchQuota(true)])
    } else {
      message.error(res.data.message || '删除失败，请重试')
    }
  } catch {
    if (isCurrentIdentity(id, version) && sequence === deleteSequence) message.error('删除失败，请重试')
  } finally {
    if (isCurrentIdentity(id, version) && sequence === deleteSequence) deleting.value = false
  }
}

watch(
  [userId, () => loginUserStore.authVersion],
  () => {
    profileSequence += 1
    worksSequence += 1
    deleteSequence += 1
    saving.value = false
    deleting.value = false
    worksLoading.value = false
    apps.value = []
    worksError.value = ''
    worksPage.current = 1
    worksPage.total = 0
    appName.value = ''
    appliedName.value = ''
    detailOpen.value = false
    selectedApp.value = undefined
    resetProfile()
    if (userId.value) {
      void quotaStore.fetchQuota()
      if (activeTab.value === 'works') void loadWorks()
    }
  },
  { immediate: true },
)
watch(activeTab, (tab) => {
  if (!userId.value) return
  if (tab === 'works') void loadWorks()
  if (tab === 'quota') void quotaStore.fetchQuota()
})
onUnmounted(() => {
  disposed = true
  profileSequence += 1
  worksSequence += 1
  deleteSequence += 1
})
</script>

<template>
  <div id="userCenterPage">
    <h1>个人中心</h1>
    <a-result v-if="!userId" status="403" title="请先登录" sub-title="登录后可管理个人资料、作品和应用额度。">
      <template #extra><a-button type="primary" @click="router.push('/user/login')">前往登录</a-button></template>
    </a-result>
    <a-tabs v-else :active-key="activeTab" @change="changeTab">
      <a-tab-pane key="profile" tab="个人资料">
        <a-form
          ref="profileForm"
          :model="profile"
          :rules="rules"
          layout="vertical"
          class="profile-form"
          @finish="saveProfile"
        >
          <a-descriptions title="账号信息" :column="1" bordered size="small" class="account-info">
            <a-descriptions-item label="账号">{{ loginUserStore.loginUser.userAccount || '—' }}</a-descriptions-item>
            <a-descriptions-item label="角色">{{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}</a-descriptions-item>
            <a-descriptions-item label="注册时间">{{ formatTime(loginUserStore.loginUser.createTime) || '—' }}</a-descriptions-item>
          </a-descriptions>
          <a-form-item label="昵称" name="userName" required>
            <a-input v-model:value="profile.userName" :disabled="saving" :maxlength="20" show-count placeholder="2～20 个字符" data-testid="profile-name" />
          </a-form-item>
          <a-form-item label="个人简介" name="userProfile">
            <a-textarea v-model:value="profile.userProfile" :disabled="saving" :maxlength="200" :rows="4" show-count placeholder="介绍一下自己（选填）" />
          </a-form-item>
          <a-form-item label="头像链接" name="userAvatar" extra="仅支持 HTTPS 图片链接，最多 1024 个字符；留空使用默认头像。">
            <a-input v-model:value="profile.userAvatar" :disabled="saving" :maxlength="1024" placeholder="https://example.com/avatar.png" />
          </a-form-item>
          <div class="avatar-preview">
            <a-avatar :src="avatarPreview" :size="64" :load-error="() => { avatarFailed = true; return false }">
              <template #icon><UserOutlined /></template>
            </a-avatar>
            <span>头像预览（图片不可用时显示默认头像）</span>
          </div>
          <a-space>
            <a-button type="primary" html-type="submit" :loading="saving" :disabled="!profileDirty" data-testid="profile-save">保存资料</a-button>
            <a-button :disabled="saving || !profileDirty" @click="resetProfile">重置</a-button>
          </a-space>
        </a-form>
      </a-tab-pane>
      <a-tab-pane key="works" tab="我的作品">
        <a-form layout="inline" class="works-search" @submit="searchWorks">
          <a-form-item label="应用名称"><a-input v-model:value="appName" allow-clear placeholder="搜索我的应用" :maxlength="80" /></a-form-item>
          <a-form-item><a-button type="primary" html-type="submit" :loading="worksLoading">搜索</a-button></a-form-item>
          <a-form-item><a-button @click="router.push('/')">创建新应用</a-button></a-form-item>
        </a-form>
        <a-alert v-if="worksError" type="error" :message="worksError" show-icon class="section-notice">
          <template #action><a-button size="small" :loading="worksLoading" @click="loadWorks">重试</a-button></template>
        </a-alert>
        <a-spin :spinning="worksLoading || deleting">
          <div v-if="apps.length" class="works-grid">
            <div v-for="app in apps" :key="app.id" class="work-item">
              <AppCard :app="app" @view-chat="viewChat" @view-work="viewWork" />
              <a-space wrap class="work-actions">
                <a-button v-if="ownsApp(app)" type="primary" @click="continueCreating(app)" data-testid="work-continue">继续创作</a-button>
                <a-button v-if="ownsApp(app)" @click="showDetail(app)" data-testid="work-detail">详情管理</a-button>
                <a-button v-if="app.deployKey" @click="viewWork(app)">查看作品</a-button>
              </a-space>
            </div>
          </div>
          <a-empty v-else-if="!worksLoading && !worksError" :description="appliedName ? '没有找到匹配的作品' : '还没有作品，去创建第一个应用吧'" />
        </a-spin>
        <p v-if="deleting" role="status">正在删除应用并刷新额度，请稍候…</p>
        <a-pagination
          v-if="worksPage.total > 0"
          :current="worksPage.current"
          :page-size="worksPage.pageSize"
          :total="worksPage.total"
          :page-size-options="['8', '12', '20']"
          show-size-changer
          :show-total="(total: number) => `共 ${total} 个应用`"
          class="works-pagination"
          @change="changeWorksPage"
        />
      </a-tab-pane>
      <a-tab-pane key="quota" tab="应用额度">
        <a-spin :spinning="quotaStore.loading">
          <a-alert v-if="quotaStore.error" type="error" :message="quotaStore.error" show-icon class="section-notice">
            <template #action><a-button size="small" :loading="quotaStore.loading" @click="quotaStore.fetchQuota(true)">重试</a-button></template>
          </a-alert>
          <a-descriptions v-if="quotaStore.quota" title="当前应用额度" :column="1" bordered data-testid="my-app-quota">
            <a-descriptions-item label="已使用">{{ quotaStore.quota.usedCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="额度上限">{{ quotaStore.quota.limitCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="剩余可创建">{{ quotaStore.quota.remainingCount }} 个应用</a-descriptions-item>
            <a-descriptions-item label="额度来源">{{ quotaStore.quota.usesDefault ? '继承系统默认额度' : '管理员单独设置' }}（系统默认 {{ quotaStore.quota.defaultLimit }} 个）</a-descriptions-item>
            <a-descriptions-item label="创建状态">{{ quotaStore.quota.canCreate ? '可以创建新应用' : '暂时无法创建新应用，请管理已有作品' }}</a-descriptions-item>
          </a-descriptions>
          <a-empty v-else-if="!quotaStore.loading && !quotaStore.error" description="暂无额度信息" />
        </a-spin>
        <a-alert
          v-if="quotaStore.quota?.overLimit"
          type="warning"
          show-icon
          message="现有应用数量超过当前上限"
          :description="`实际已使用 ${quotaStore.quota.usedCount} 个，上限 ${quotaStore.quota.limitCount} 个。已有作品保留，但需使应用数量低于上限才能继续创建。`"
          class="section-notice"
        />
        <section class="quota-rules">
          <h2>额度规则</h2>
          <ul>
            <li>额度统计当前未删除的应用数量，草稿和未部署的应用同样占用额度。</li>
            <li>删除应用后释放对应创建额度，继续编辑已有作品不额外占用额度。</li>
            <li>删除释放的是应用数量额度，不代表生成文件或已部署文件已被清理。</li>
            <li>历史作品或管理员调低上限可能产生超额状态，例如已使用 12 个、上限 10 个；这里始终展示真实数量。</li>
          </ul>
          <a-space wrap>
            <RouterLink to="/user/center?tab=works">管理我的作品</RouterLink>
            <RouterLink to="/">返回首页创建应用</RouterLink>
            <a-button type="link" :loading="quotaStore.loading" @click="quotaStore.fetchQuota(true)">刷新额度</a-button>
          </a-space>
        </section>
      </a-tab-pane>
    </a-tabs>
    <AppDetailModal
      v-if="userId"
      v-model:open="detailOpen"
      :app="selectedApp"
      :show-actions="selectedOwned && !deleting"
      @edit="editSelectedApp"
      @delete="deleteSelectedApp"
    />
  </div>
</template>

<style scoped>
#userCenterPage {
  max-width: 1200px;
  margin: 24px auto;
  padding: 24px;
  background: #fff;
  border-radius: 8px;
}
h1 { font-size: 24px; margin-bottom: 16px; }
.profile-form { max-width: 640px; }
.account-info { margin-bottom: 24px; }
.avatar-preview { display: flex; align-items: center; gap: 16px; margin: 24px 0; color: #666; }
.works-search { row-gap: 12px; margin-bottom: 24px; }
.works-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 24px; }
.work-item { min-width: 0; }
.work-actions { display: flex; padding-top: 16px; }
.works-pagination { margin-top: 24px; text-align: right; }
.section-notice { margin: 16px 0; }
.quota-rules { margin-top: 24px; line-height: 1.8; }
.quota-rules h2 { font-size: 18px; }
@media (max-width: 640px) {
  #userCenterPage { margin: 12px; padding: 16px; }
  .works-grid { grid-template-columns: 1fr; }
  .avatar-preview { align-items: flex-start; }
}
</style>
