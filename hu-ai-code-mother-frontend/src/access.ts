import { useLoginUserStore } from '@/stores/loginUser'
import { message } from 'ant-design-vue'
import router from '@/router'
import { getSafeRedirect } from '@/utils/redirect'

/** 个人页面需要登录；公开作品查看、登录和注册保持公开。 */
router.beforeEach(async (to) => {
  const loginUserStore = useLoginUserStore()
  if (!loginUserStore.hasFetched || loginUserStore.error) {
    await loginUserStore.fetchLoginUser()
  }
  const loginUser = loginUserStore.loginUser
  const isAdminPage = to.path.startsWith('/admin')
  if ((to.meta.requiresAuth || isAdminPage) && !loginUser.id) {
    message.warning(loginUserStore.error || '请先登录')
    return { path: '/user/login', query: { redirect: getSafeRedirect(to.fullPath) } }
  }
  if (isAdminPage && loginUser.userRole !== 'admin') {
    message.error('没有权限')
    return '/'
  }
})
