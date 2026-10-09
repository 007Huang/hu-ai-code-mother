<template>
  <a-layout-header class="header">
    <a-row :wrap="false">
      <!-- 左侧：Logo和标题 -->
      <a-col flex="200px">
        <RouterLink to="/">
          <div class="header-left">
            <img class="logo" src="@/assets/logo.png" alt="Logo" />
            <h1 class="site-title">小牛应用生成</h1>
          </div>
        </RouterLink>
      </a-col>
      <!-- 中间：导航菜单 -->
      <a-col flex="auto">
        <a-menu v-model:selectedKeys="selectedKeys" mode="horizontal" :items="menuItems" @click="handleMenuClick" />
      </a-col>
      <!-- 右侧：用户操作区域 -->
      <a-col>
        <div class="user-login-status">
          <div v-if="loginUserStore.loginUser.id">
            <a-dropdown v-model:open="accountMenuOpen" :trigger="['click']" overlay-class-name="account-dropdown" @open-change="handleAccountOpen">
              <a-button
                type="text"
                class="account-trigger"
                aria-label="打开账户菜单"
                aria-haspopup="menu"
                :aria-expanded="accountMenuOpen"
                data-testid="account-menu-trigger"
                @keydown.down.prevent="accountMenuOpen = true; handleAccountOpen(true)"
                @keydown.esc="accountMenuOpen = false"
              >
                <a-space>
                  <a-avatar :src="loginUserStore.loginUser.userAvatar">
                    <template #icon><UserOutlined /></template>
                  </a-avatar>
                  <span class="account-name">{{ loginUserStore.loginUser.userName ?? '无名' }}</span>
                </a-space>
              </a-button>
              <template #overlay>
                <a-menu>
                  <a-menu-item key="identity" disabled class="account-summary">
                    <div>{{ loginUserStore.loginUser.userName || '无名' }}</div>
                    <div class="account-muted">{{ loginUserStore.loginUser.userAccount || '已登录' }}</div>
                    <div>{{ loginUserStore.loginUser.userRole === 'admin' ? '管理员' : '普通用户' }}</div>
                  </a-menu-item>
                  <a-menu-item key="quota-summary" disabled class="account-summary">
                    <span v-if="quotaStore.loading" role="status">正在加载应用额度…</span>
                    <span v-else-if="quotaStore.error">应用额度加载失败</span>
                    <span v-else-if="quotaStore.quota">应用 {{ quotaStore.quota.usedCount }} / {{ quotaStore.quota.limitCount }}，剩余 {{ quotaStore.quota.remainingCount }}</span>
                    <span v-else>应用额度暂不可用</span>
                  </a-menu-item>
                  <a-menu-item v-if="quotaStore.error" key="quota-retry" @click="quotaStore.fetchQuota(true)">重试加载额度</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="profile" @click="openCenter('profile')"><UserOutlined /> 个人资料</a-menu-item>
                  <a-menu-item key="works" @click="openCenter('works')">我的作品</a-menu-item>
                  <a-menu-item key="quota" @click="openCenter('quota')">应用额度</a-menu-item>
                  <a-menu-divider />
                  <a-menu-item key="logout" :disabled="loggingOut" @click="doLogout">
                    <LogoutOutlined />
                    {{ loggingOut ? '正在退出…' : '退出登录' }}
                  </a-menu-item>
                </a-menu>
              </template>
            </a-dropdown>
          </div>
          <div v-else>
            <a-button type="primary" href="/user/login">登录</a-button>
          </div>
        </div>
      </a-col>
    </a-row>
  </a-layout-header>
</template>

<script setup lang="ts">
import { computed, h, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { type MenuProps, message } from 'ant-design-vue'
import { useLoginUserStore } from '@/stores/loginUser.ts'
import { useAppQuotaStore } from '@/stores/appQuota'
import { userLogout } from '@/api/userController.ts'
import { LogoutOutlined, HomeOutlined, UserOutlined } from '@ant-design/icons-vue'

const loginUserStore = useLoginUserStore()
const quotaStore = useAppQuotaStore()
const router = useRouter()
const accountMenuOpen = ref(false)
const loggingOut = ref(false)
let disposed = false
const handleAccountOpen = (open: boolean) => {
  if (open && loginUserStore.loginUser.id) void quotaStore.fetchQuota()
}
const openCenter = (tab: 'profile' | 'works' | 'quota') => {
  accountMenuOpen.value = false
  void router.push({ path: '/user/center', query: { tab } })
}
watch(() => loginUserStore.authVersion, () => { accountMenuOpen.value = false })
// 当前选中菜单
const selectedKeys = ref<string[]>(['/'])
// 监听路由变化，更新当前选中菜单
const removeRouteHook = router.afterEach((to) => {
  selectedKeys.value = [to.path]
})
onUnmounted(() => {
  disposed = true
  removeRouteHook()
})

// 菜单配置项
const originItems = [
  {
    key: '/',
    icon: () => h(HomeOutlined),
    label: '主页',
    title: '主页',
  },
  {
    key: '/admin/userManage',
    label: '用户管理',
    title: '用户管理',
  },
  {
    key: '/admin/appManage',
    label: '应用管理',
    title: '应用管理',
  },
  {
    // key: 'others',
    key: 'more-creation',
    label: h('a', { href: 'https://ai.qiyaov.com/', target: '_blank' }, '更多创作'),
    title: '更多创作',
  },
  {
    key: 'api-platform',
    label: h('a', { href: 'https://platform.qiyaov.com/', target: '_blank' }, 'API服务平台'),
    title: 'API服务平台',
  },
]

// 过滤菜单项
const filterMenus = (menus = [] as MenuProps['items']) => {
  return menus?.filter((menu) => {
    const menuKey = menu?.key as string
    if (menuKey?.startsWith('/admin')) {
      const loginUser = loginUserStore.loginUser
      if (!loginUser || loginUser.userRole !== 'admin') {
        return false
      }
    }
    return true
  })
}

// 展示在菜单的路由数组
const menuItems = computed<MenuProps['items']>(() => filterMenus(originItems))

// 处理菜单点击
const handleMenuClick: MenuProps['onClick'] = (e) => {
  const key = e.key as string
  selectedKeys.value = [key]
  // 跳转到对应页面
  if (key.startsWith('/')) {
    router.push(key)
  }
}

// 退出登录
const doLogout = async () => {
  if (loggingOut.value) return
  const version = loginUserStore.authVersion
  loggingOut.value = true
  try {
    const res = await userLogout()
    if (disposed || version !== loginUserStore.authVersion) return
    if (res.data.code === 0) {
      loginUserStore.setLoginUser({ userName: '未登录' })
      quotaStore.reset()
      accountMenuOpen.value = false
      message.success('退出登录成功')
      await router.push('/user/login')
    } else {
      message.error('退出登录失败，' + res.data.message)
    }
  } catch {
    if (!disposed && version === loginUserStore.authVersion) message.error('退出登录失败，请重试')
  } finally {
    if (!disposed) loggingOut.value = false
  }
}
</script>

<style scoped>
.header {
  background: #fff;
  padding: 0 24px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.logo {
  height: 48px;
  width: 48px;
}

.site-title {
  margin: 0;
  font-size: 18px;
  color: #1890ff;
}

.ant-menu-horizontal {
  border-bottom: none !important;
}

.account-trigger {
  height: auto;
  padding: 4px 8px;
}

.account-name {
  display: inline-block;
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: middle;
  white-space: nowrap;
}

:global(.account-dropdown .account-summary) {
  cursor: default;
  color: #262626 !important;
  white-space: normal;
  max-width: 300px;
  overflow-wrap: anywhere;
}

:global(.account-dropdown .account-muted) {
  color: #666;
  font-size: 12px;
}
</style>
