<template>
  <AppLayout>
    <div class="hot-header">
      <h3>🔥 热门榜</h3>
      <span class="tip">热度 = 点赞×3 + 评论×5 + 浏览×1（互动事件经 Outbox→RocketMQ 异步更新，点赞后稍等几秒榜单会变化）</span>
    </div>
    <div v-if="list.length === 0 && !loading" class="empty">暂无热度数据，去首页点几个赞吧</div>
    <div class="hot-list">
      <div v-for="item in list" :key="item.video.id" class="hot-item" @click="$router.push('/video/' + item.video.id)">
        <div class="rank" :class="'top' + item.rank">{{ item.rank }}</div>
        <img class="cover" :src="item.video.coverUrl" loading="lazy" alt="" />
        <div class="meta">
          <div class="title">{{ item.video.title }}</div>
          <div class="sub">{{ item.video.author?.nickname || item.video.author?.username }} · 👁 {{ formatCount(item.video.viewCount) }} · 💬 {{ formatCount(item.video.commentCount) }}</div>
        </div>
        <div class="score">🔥 {{ formatCount(item.score) }}</div>
      </div>
    </div>
    <div v-if="loading" class="empty">加载中…</div>
  </AppLayout>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import { formatCount } from '../utils/format'
import http from '../api/request'

const list = ref([])
const loading = ref(false)

onMounted(async () => {
  loading.value = true
  try {
    list.value = await http.get('/feed/hot', { params: { size: 30 } })
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
})
</script>

<style scoped>
.hot-header { display: flex; align-items: baseline; gap: 12px; margin-bottom: 14px; flex-wrap: wrap; }
h3 { margin: 0; color: #e6544a; }
.tip { font-size: 12px; color: #999; }
.hot-list { background: #fff; border-radius: 10px; overflow: hidden; }
.hot-item { display: flex; align-items: center; gap: 14px; padding: 10px 14px; cursor: pointer; border-bottom: 1px solid #f2f2f2; }
.hot-item:hover { background: #fafafa; }
.rank { width: 28px; text-align: center; font-size: 17px; font-weight: 800; color: #bbb; font-style: italic; }
.rank.top1 { color: #ff4d3a; } .rank.top2 { color: #ff8a3a; } .rank.top3 { color: #ffb03a; }
.cover { width: 120px; aspect-ratio: 16/9; object-fit: cover; border-radius: 6px; background: #ddd; }
.meta { flex: 1; min-width: 0; }
.title { font-size: 14px; color: #222; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.sub { font-size: 12px; color: #999; margin-top: 6px; }
.score { font-size: 13px; color: #e6544a; font-weight: 600; white-space: nowrap; }
.empty { text-align: center; color: #888; padding: 60px 0; }
</style>
