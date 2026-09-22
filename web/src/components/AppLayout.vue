<template>
  <div class="layout">
    <header class="header">
      <div class="header-inner">
        <div class="brand" @click="$router.push('/')">▶ 短视频 Feed</div>
        <nav class="nav">
          <router-link to="/" exact-active-class="active">首页</router-link>
          <router-link to="/following" exact-active-class="active">关注</router-link>
          <router-link to="/hot" exact-active-class="active">热榜</router-link>
        </nav>
        <div class="right">
          <slot name="action" />
          <el-dropdown @command="onCommand">
            <span class="user-chip">
              <el-avatar :size="30" :src="auth.user?.avatarUrl" />
              <span class="nickname">{{ auth.user?.nickname || auth.user?.username }}</span>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="logout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </div>
    </header>
    <main class="main"><slot /></main>
  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import http from '../api/request'

const auth = useAuthStore()
const router = useRouter()

async function onCommand(cmd) {
  if (cmd !== 'logout') return
  try {
    await http.post('/auth/logout')
  } catch (e) {
    // 登出接口失败不阻塞本地清理
  }
  auth.clear()
  ElMessage.success('已退出登录')
  router.push('/login')
}
</script>

<style scoped>
.header { position: sticky; top: 0; z-index: 100; background: #fff; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.header-inner { max-width: 1080px; margin: 0 auto; display: flex; align-items: center; gap: 28px; padding: 0 16px; height: 56px; }
.brand { font-size: 18px; font-weight: 700; color: #e6544a; cursor: pointer; white-space: nowrap; }
.nav { display: flex; gap: 20px; flex: 1; }
.nav a { color: #555; font-size: 15px; padding: 4px 2px; border-bottom: 2px solid transparent; }
.nav a.active { color: #111; font-weight: 600; border-bottom-color: #e6544a; }
.right { display: flex; align-items: center; gap: 14px; }
.user-chip { display: flex; align-items: center; gap: 8px; cursor: pointer; outline: none; }
.nickname { font-size: 14px; color: #333; }
.main { max-width: 1080px; margin: 0 auto; padding: 20px 16px 60px; }
</style>
