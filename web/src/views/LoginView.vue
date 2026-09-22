<template>
  <div class="login-page">
    <div class="panel">
      <h2>▶ 短视频 Feed</h2>
      <el-tabs v-model="tab" stretch>
        <el-tab-pane label="登录" name="login">
          <el-form :model="loginForm" label-position="top" @keyup.enter="doLogin">
            <el-form-item label="用户名">
              <el-input v-model="loginForm.username" placeholder="demo" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="loginForm.password" type="password" show-password placeholder="123456" />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doLogin">登 录</el-button>
          </el-form>
        </el-tab-pane>
        <el-tab-pane label="注册" name="register">
          <el-form :model="regForm" label-position="top">
            <el-form-item label="用户名">
              <el-input v-model="regForm.username" placeholder="3~20 位" />
            </el-form-item>
            <el-form-item label="昵称">
              <el-input v-model="regForm.nickname" placeholder="展示昵称" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="regForm.password" type="password" show-password placeholder="至少 6 位" />
            </el-form-item>
            <el-button type="primary" class="submit" :loading="loading" @click="doRegister">注 册</el-button>
          </el-form>
        </el-tab-pane>
      </el-tabs>
      <div class="hint">演示账号：demo / 123456（另有 alice、bob）</div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import http from '../api/request'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()
const tab = ref('login')
const loading = ref(false)
const loginForm = reactive({ username: '', password: '' })
const regForm = reactive({ username: '', nickname: '', password: '' })

async function doLogin() {
  if (!loginForm.username || !loginForm.password) return ElMessage.warning('请输入用户名和密码')
  loading.value = true
  try {
    const pair = await http.post('/auth/login', loginForm)
    auth.setTokens(pair)
    ElMessage.success('登录成功')
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function doRegister() {
  if (!regForm.username || !regForm.nickname || !regForm.password) return ElMessage.warning('请填写完整')
  loading.value = true
  try {
    const pair = await http.post('/auth/register', regForm)
    auth.setTokens(pair)
    ElMessage.success('注册成功，已自动登录')
    router.push('/')
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page { min-height: 100vh; display: flex; align-items: center; justify-content: center;
  background: linear-gradient(135deg, #ff6b5e 0%, #e6544a 40%, #7b4dff 100%); }
.panel { width: 380px; background: #fff; border-radius: 14px; padding: 28px 30px 20px; box-shadow: 0 12px 40px rgba(0,0,0,.18); }
h2 { text-align: center; color: #e6544a; margin: 4px 0 18px; }
.submit { width: 100%; margin-top: 4px; }
.hint { text-align: center; font-size: 12px; color: #999; margin-top: 12px; }
</style>
