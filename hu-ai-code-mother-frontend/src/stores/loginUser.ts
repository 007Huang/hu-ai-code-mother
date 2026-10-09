import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getLoginUser } from '@/api/userController'
import { advanceAuthRevision, getAuthRevision, onSessionExpired } from '@/utils/authState'

/** 登录用户信息；请求结果必须属于当前会话，避免登出后回填旧资料。 */
export const useLoginUserStore = defineStore('loginUser', () => {
  const loginUser = ref<API.LoginUserVO>({ userName: '未登录' })
  const loading = ref(false)
  const hasFetched = ref(false)
  const error = ref('')
  const authVersion = ref(getAuthRevision())
  let requestVersion = 0
  let pending: Promise<void> | null = null

  function clearLoginUser() {
    requestVersion += 1
    authVersion.value = advanceAuthRevision()
    loginUser.value = { userName: '未登录' }
    hasFetched.value = true
    loading.value = false
    error.value = ''
    pending = null
  }

  function setLoginUser(newLoginUser: API.LoginUserVO) {
    requestVersion += 1
    if (String(loginUser.value.id ?? '') !== String(newLoginUser.id ?? '')) {
      authVersion.value = advanceAuthRevision()
    }
    loginUser.value = newLoginUser
    hasFetched.value = true
    loading.value = false
    error.value = ''
    pending = null
  }

  function fetchLoginUser(): Promise<void> {
    if (pending) {
      return pending
    }
    const version = ++requestVersion
    const revision = authVersion.value
    loading.value = true
    error.value = ''
    const task = (async () => {
      try {
        const res = await getLoginUser()
        if (version !== requestVersion || revision !== authVersion.value) {
          return
        }
        if (res.data.code === 0 && res.data.data) {
          setLoginUser(res.data.data)
        } else if (res.data.code === 40100) {
          clearLoginUser()
        } else {
          error.value = res.data.message || '登录信息加载失败，请重试'
        }
      } catch {
        if (version === requestVersion && revision === authVersion.value) {
          error.value = '登录信息加载失败，请检查网络后重试'
        }
      } finally {
        if (version === requestVersion) {
          hasFetched.value = true
          loading.value = false
        }
        if (version === requestVersion) {
          pending = null
        }
      }
    })()
    pending = task
    return task
  }

  onSessionExpired(clearLoginUser)
  return { loginUser, loading, hasFetched, error, authVersion, fetchLoginUser, setLoginUser, clearLoginUser }
})
