import axios from 'axios'
import { message } from 'ant-design-vue'
import { API_BASE_URL } from '@/config/env'
import { getAuthRevision, notifySessionExpired } from '@/utils/authState'
import { getLoginRedirect } from '@/utils/redirect'

declare module 'axios' {
  interface AxiosRequestConfig {
    authRevision?: number
  }
}

const myAxios = axios.create({
  baseURL: API_BASE_URL,
  timeout: 60000,
  withCredentials: true,
})

myAxios.interceptors.request.use((config) => {
  config.authRevision = getAuthRevision()
  return config
})

function handleSessionExpired(url: string, requestRevision: number) {
  if (!notifySessionExpired(requestRevision)) {
    return
  }
  if (!url.includes('/user/get/login') && !/^\/user\/(login|register)(\/|$)/.test(window.location.pathname)) {
    message.warning('登录状态已失效，请重新登录')
    const path = `${window.location.pathname}${window.location.search}${window.location.hash}`
    window.location.assign(getLoginRedirect(path))
  }
}

myAxios.interceptors.response.use(
  (response) => {
    if (response.data.code === 40100) {
      handleSessionExpired(response.config.url || '', response.config.authRevision ?? -1)
    }
    return response
  },
  (error: unknown) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      handleSessionExpired(error.config?.url || '', error.config?.authRevision ?? -1)
    }
    return Promise.reject(error)
  },
)

export default myAxios
