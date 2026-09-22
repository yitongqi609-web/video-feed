<template>
  <AppLayout>
    <template #action>
      <el-button type="primary" round @click="publishVisible = true">＋ 发布</el-button>
    </template>

    <div class="feed-grid">
      <VideoCard v-for="item in items" :key="item.video.id" :item="item" @toggle-like="toggleLike" />
    </div>

    <div ref="sentinel" class="sentinel">
      <span v-if="loading">加载中…</span>
      <span v-else-if="!hasMore">— 到底啦，去发布一个吧 —</span>
    </div>

    <el-dialog v-model="publishVisible" title="发布视频" width="460px">
      <el-form :model="form" label-position="top">
        <el-form-item label="标题" required>
          <el-input v-model="form.title" maxlength="100" placeholder="一句话标题" />
        </el-form-item>
        <el-form-item label="简介">
          <el-input v-model="form.description" type="textarea" :rows="2" maxlength="500" />
        </el-form-item>
        <el-form-item label="视频 URL" required>
          <el-input v-model="form.videoUrl" placeholder="mp4 地址" />
        </el-form-item>
        <el-form-item label="封面 URL">
          <el-input v-model="form.coverUrl" placeholder="留空自动生成" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="doPublish">发 布</el-button>
      </template>
    </el-dialog>
  </AppLayout>
</template>

<script setup>
import { onMounted, onUnmounted, reactive, ref } from 'vue'
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

// 无限滚动：IntersectionObserver 盯住列表底部的哨兵元素
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
    const page = await http.get('/feed', { params: { cursor: cursor.value, limit: 12 } })
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
  item.liked = !was // 乐观更新，失败回滚
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

const publishVisible = ref(false)
const publishing = ref(false)
const form = reactive({
  title: '',
  description: '',
  videoUrl: 'https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerJoyrides.mp4',
  coverUrl: ''
})

async function doPublish() {
  if (!form.title || !form.videoUrl) return ElMessage.warning('标题和视频 URL 必填')
  publishing.value = true
  try {
    const video = await http.post('/videos', form)
    publishVisible.value = false
    ElMessage.success('发布成功')
    // 新视频雪花ID最大，插到最前面即可
    items.value.unshift({ video, liked: false })
    form.title = ''
    form.description = ''
  } catch (e) {
    ElMessage.error(e.message)
  } finally {
    publishing.value = false
  }
}
</script>

<style scoped>
.feed-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 16px; }
.sentinel { text-align: center; color: #999; font-size: 13px; padding: 26px 0 10px; }
</style>
