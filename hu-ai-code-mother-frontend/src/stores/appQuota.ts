import { defineStore } from 'pinia'
import { ref, watch } from 'vue'
import { getMyAppQuota } from '@/api/appController'
import { useLoginUserStore } from '@/stores/loginUser'

/** 共享本人额度，不使用作品搜索结果或本地增减推算。 */
export const useAppQuotaStore = defineStore('appQuota', () => {
  const loginUserStore = useLoginUserStore()
  const quota = ref<API.AppQuotaVO | null>(null)
  const loading = ref(false)
  const error = ref('')
  let requestVersion = 0
  let pending: Promise<void> | null = null

  function reset() {
    requestVersion += 1
    quota.value = null
    error.value = ''
    loading.value = false
    pending = null
  }

  function fetchQuota(force = false): Promise<void> {
    const userId = String(loginUserStore.loginUser.id ?? '')
    if (!userId) {
      reset()
      return Promise.resolve()
    }
    if (pending && !force) {
      return pending
    }
    const version = ++requestVersion
    const revision = loginUserStore.authVersion
    loading.value = true
    error.value = ''
    const isCurrent = () =>
      version === requestVersion &&
      userId === String(loginUserStore.loginUser.id ?? '') &&
      revision === loginUserStore.authVersion
    const task = (async () => {
      try {
        const res = await getMyAppQuota()
        if (!isCurrent()) {
          return
        }
        if (res.data.code === 0 && res.data.data) {
          quota.value = res.data.data
        } else {
          quota.value = null
          error.value = res.data.message || '作品额度加载失败'
        }
      } catch {
        if (isCurrent()) {
          quota.value = null
          error.value = '作品额度加载失败，请检查网络后重试'
        }
      } finally {
        if (isCurrent()) {
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

  watch(() => loginUserStore.authVersion, reset, { flush: 'sync' })
  return { quota, loading, error, fetchQuota, reset }
})
