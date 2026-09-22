<template>
  <AppLayout>
    <div v-if="!loading && items.length === 0" class="empty">
      <p>关注的人还没有发过视频</p>
      <el-button type="primary" round @click="$router.push('/')">去首页逛逛</el-button>
    </div>
    <div v-else class="feed-grid">
      <VideoCard v-for="item in items" :key="item.video.id" :item="item" @toggle-like="toggleLike" />
    </div>
    <div ref="sentinel" class="sentinel">
      <span v-if="loading">加载中…</span>
      <span v-else-if="!hasMore && items.length > 0">— 到底啦 —</span>
    </div>
  </AppLayout>
</template>

<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import AppLayout from '../components/AppLayout.vue'
import VideoCard from '../components/VideoCard.vue'
import http from '../api/request'

const items = ref([])
const cursor = ref(0)
const hasMore = ref(true)
const loading = ref(false)
const sentinel = ref(null)
let observer = null

onMounted(async () => {
  await loadMore()
  observer = new IntersectionObserver((entries) => {
    if (entries[0].isIntersecting && hasMore.value && !loading.value) loadMore()
  })
  if (sentinel.value) observer.observe(sentinel.value)
})
onUnmounted(() => observer && observer.disconnect())

async function loadMore() {
  if (loading.value) return
  loading.value = true
  try {
    const page = await http.get('/feed/following', { params: { cursor: cursor.value, limit: 12 } })
    items.value.push(...page.list)
    cursor.value = page.nextCursor
    hasMore.value = page.hasMore
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    loading.value = false
  }
}

async function toggleLike(item) {
  const id = item.video.id
  const was = item.liked
  item.liked = !was
  try {
    if (was) {
      await http.delete(`/videos/${id}/like`)
      item.video.likeCount--
    } else {
      await http.post(`/videos/${id}/like`)
      item.video.likeCount++
    }
  } catch (e) {
    item.liked = was
    ElMessage.error(e.message)
  }
}
</script>

<style scoped>
.feed-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.sentinel { text-align: center; color: #999; font-size: 13px; padding: 26px 0 10px; }
.empty { text-align: center; color: #888; padding: 80px 0; }
</style>
