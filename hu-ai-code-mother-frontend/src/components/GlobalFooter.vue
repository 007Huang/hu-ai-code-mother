<template>
  <a-layout-footer class="footer">
    <div class="footer-content">
      <p class="copyright">
        <a href="https://github.com/007Huang/hu-ai-code-mother" target="_blank" rel="noopener noreferrer"
          class="author-link">
          Github项目源码 by Huang
        </a>
      </p>
      <p class="icp">
        <a href="https://beian.miit.gov.cn/" target="_blank" rel="noopener noreferrer">
          赣ICP备2026019123号-1
        </a>
        <span class="sep">|</span>
        <a href="https://beian.mps.gov.cn/#/query/webSearch?code=36010602000422" rel="noreferrer" target="_blank">
          <img class="police-icon" src="@/assets/police.png" alt="公安备案图标" />
          赣公网安备36010602000422号
        </a>
      </p>
    </div>
  </a-layout-footer>
</template>

<script setup lang="ts">
// 无需额外的响应式数据
</script>

<style scoped>
.footer {
  background: rgba(255, 255, 255, 0.8);
  backdrop-filter: blur(10px);
  text-align: center;
  padding: 20px;
  margin-top: 40px;
  border-top: 1px solid rgba(102, 126, 234, 0.1);
}

.copyright {
  margin: 0;
  color: #666;
  font-size: 14px;
}

.icp {
  margin: 8px 0 0;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
}

.icp a {
  color: #999;
  text-decoration: none;
  display: inline-flex;
  align-items: center;
}

.icp a:hover {
  color: #666;
}

.sep {
  color: #ccc;
}

.police-icon {
  width: 14px;
  height: 14px;
  margin-right: 4px;
  vertical-align: middle;
}
</style>
