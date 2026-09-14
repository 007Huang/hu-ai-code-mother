/**
 * 环境变量配置
 */

import { CodeGenTypeEnum } from "@/utils/codeGenTypes"

// 应用部署域名
export const DEPLOY_DOMAIN = import.meta.env.VITE_DEPLOY_DOMAIN || 'http://localhost'

// API 基础地址
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8123/api'

// 静态资源地址
export const STATIC_BASE_URL = `${API_BASE_URL}/static`

// 获取部署应用的完整URL
export const getDeployUrl = (deployKey: string) => {
  return `${DEPLOY_DOMAIN}/${deployKey}`
}

// 获取静态资源预览 URL；revision 用于生成完成后绕过旧预览缓存
export const getStaticPreviewUrl = (codeGenType: string, appId: string, revision?: number) => {
  const baseUrl = `${STATIC_BASE_URL}/${codeGenType}_${appId}/`
  const previewUrl = codeGenType === CodeGenTypeEnum.VUE_PROJECT
    ? `${baseUrl}dist/index.html`
    : baseUrl
  return revision === undefined ? previewUrl : `${previewUrl}?v=${revision}`
}