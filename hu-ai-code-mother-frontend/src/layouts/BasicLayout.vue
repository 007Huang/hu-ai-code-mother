<template>
  <a-layout class="basic-layout">
    <!-- 顶部导航栏 -->
    <GlobalHeader />
    <!-- 主要内容区域 -->
    <a-layout-content class="main-content">
      <router-view />
    </a-layout-content>
    <!-- 底部版权信息 -->
    <GlobalFooter />
  </a-layout>
</template>

<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import GlobalHeader from '@/components/GlobalHeader.vue'
import GlobalFooter from '@/components/GlobalFooter.vue'
import { useAppQuotaStore } from '@/stores/appQuota'
import { useLoginUserStore } from '@/stores/loginUser'

const quotaStore = useAppQuotaStore()
const loginUserStore = useLoginUserStore()
const refreshQuota = () => {
  if (loginUserStore.loginUser.id && document.visibilityState === 'visible') {
    void quotaStore.fetchQuota(true)
  }
}
onMounted(() => window.addEventListener('focus', refreshQuota))
onUnmounted(() => window.removeEventListener('focus', refreshQuota))
</script>

<style scoped>
.basic-layout {
  background: none;
}

.main-content {
  width: 100%;
  padding: 0;
  background: none;
  margin: 0;
}
</style>
