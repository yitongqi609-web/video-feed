/**
 * 后端全链路冒烟测试：node scripts/smoke-test.mjs
 * 覆盖：登录 → Feed 游标分页 → 详情缓存 → 点赞唯一性 → 评论 → 关注 → 热榜 → 令牌轮换/重放 → 登出黑名单
 */
const BASE = 'http://localhost:8080/api'

let passed = 0, failed = 0
function check(name, cond, extra = '') {
  if (cond) { passed++; console.log(`  PASS  ${name}`) }
  else { failed++; console.log(`  FAIL  ${name} ${extra}`) }
}

async function api(method, path, body, token) {
  const res = await fetch(BASE + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: 'Bearer ' + token } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  })
  let json = null
  try { json = await res.json() } catch {}
  return { status: res.status, body: json }
}

console.log('== 1. 认证 ==')
const login = await api('POST', '/auth/login', { username: 'demo', password: '123456' })
check('登录成功', login.status === 200 && login.body.code === 0, JSON.stringify(login.body))
let accessToken = login.body.data.accessToken
let refreshToken = login.body.data.refreshToken
check('返回双令牌', !!accessToken && !!refreshToken)

const badLogin = await api('POST', '/auth/login', { username: 'demo', password: 'wrong' })
check('错误密码返回 401', badLogin.status === 401)

console.log('== 2. Feed 游标分页 ==')
const page1 = await api('GET', '/feed?limit=5', null, accessToken)
check('Feed 第一页 5 条 + hasMore', page1.body.data.list.length === 5 && page1.body.data.hasMore === true)
const cursor = page1.body.data.nextCursor
const page2 = await api('GET', `/feed?limit=5&cursor=${cursor}`, null, accessToken)
check('游标翻页不重复', page2.body.data.list[0].video.id < page1.body.data.list[4].video.id)
const all = [...page1.body.data.list, ...page2.body.data.list]
check('Feed 条目带作者和点赞状态', all[0].video.author != null && typeof all[0].liked === 'boolean')

console.log('== 3. 视频详情缓存 ==')
const firstVideo = page1.body.data.list[0]
const vid = firstVideo.video.id
const t0 = Date.now()
const d1 = await api('GET', `/videos/${vid}`, null, accessToken)
const t1 = Date.now()
const d2 = await api('GET', `/videos/${vid}`, null, accessToken)
const t2 = Date.now()
check('详情返回含 liked/followedAuthor', d1.body.data.liked !== undefined && d1.body.data.followedAuthor !== undefined)
check('缓存命中更快', t2 - t1 <= t1 - t0, `首次${t1 - t0}ms 二次${t2 - t1}ms`)
const notFound = await api('GET', '/videos/999999', null, accessToken)
check('不存在视频 404', notFound.status === 404)
const notFound2 = await api('GET', '/videos/999999', null, accessToken)
check('空值缓存挡住第二次穿透', notFound2.status === 404)

console.log('== 4. 点赞一致性 ==')
const notLikedYet = page2.body.data.list.find(i => !i.liked)
if (notLikedYet) {
  const id = notLikedYet.video.id
  const before = notLikedYet.video.likeCount
  const l1 = await api('POST', `/videos/${id}/like`, null, accessToken)
  check('点赞成功', l1.body.code === 0)
  const l2 = await api('POST', `/videos/${id}/like`, null, accessToken)
  check('重复点赞被唯一索引挡住(409)', l2.status === 409, JSON.stringify(l2.body))
  const detail = await api('GET', `/videos/${id}`, null, accessToken)
  check('点赞计数 +1', detail.body.data.video.likeCount === before + 1, `期望${before + 1} 实际${detail.body.data.video.likeCount}`)
  const u1 = await api('DELETE', `/videos/${id}/like`, null, accessToken)
  const detail2 = await api('GET', `/videos/${id}`, null, accessToken)
  check('取消点赞计数恢复', u1.body.code === 0 && detail2.body.data.video.likeCount === before)
}

console.log('== 5. 评论 ==')
const cm = await api('POST', `/videos/${vid}/comments`, { content: '冒烟测试评论' }, accessToken)
check('发评论成功', cm.body.code === 0 && cm.body.data.author.username === 'demo')
const cl = await api('GET', `/videos/${vid}/comments?limit=10`, null, accessToken)
check('评论列表分页', cl.body.data.list.length >= 1 && cl.body.data.list[0].id > 0)

console.log('== 6. 关注 ==')
const otherVideo = page1.body.data.list.find(i => i.video.userId !== login.body.data.user.id && !i.followedAuthor)
if (otherVideo) {
  const uid = otherVideo.video.userId
  await api('DELETE', `/users/${uid}/follow`, null, accessToken) // 种子数据可能已关注，先清理
  const f1 = await api('POST', `/users/${uid}/follow`, null, accessToken)
  check('关注成功', f1.body.code === 0)
  const f2 = await api('POST', `/users/${uid}/follow`, null, accessToken)
  check('重复关注 409', f2.status === 409)
  const ff = await api('GET', '/feed/following?limit=20', null, accessToken)
  check('关注 Feed 含该作者视频', ff.body.data.list.some(i => i.video.userId === uid))
  const profile = await api('GET', `/users/${uid}`, null, accessToken)
  check('主页显示已关注', profile.body.data.hasFollowed === true)
  await api('DELETE', `/users/${uid}/follow`, null, accessToken)
}

console.log('== 7. 热榜 ==')
const hot = await api('GET', '/feed/hot?size=10', null, accessToken)
check('热榜有序返回', hot.body.data.length >= 5 && hot.body.data[0].score >= hot.body.data[1].score)

console.log('== 8. Refresh Token 轮换 ==')
const r1 = await api('POST', '/auth/refresh', { refreshToken })
check('刷新成功返回新双令牌', r1.body.code === 0 && r1.body.data.refreshToken !== refreshToken)
const oldRefresh = refreshToken
refreshToken = r1.body.data.refreshToken
accessToken = r1.body.data.accessToken
const r2 = await api('POST', '/auth/refresh', { refreshToken: oldRefresh })
check('旧 Refresh 重放被拒(会话吊销)', r2.status === 401, JSON.stringify(r2.body))
const r3 = await api('POST', '/auth/refresh', { refreshToken })
check('被吊销后新 Refresh 也失效（安全策略）', r3.status === 401)

console.log('== 9. 登出黑名单 ==')
const reLogin = await api('POST', '/auth/login', { username: 'demo', password: '123456' })
accessToken = reLogin.body.data.accessToken
refreshToken = reLogin.body.data.refreshToken
const lo = await api('POST', '/auth/logout', null, accessToken)
check('登出成功', lo.body.code === 0)
const afterLogout = await api('GET', '/feed', null, accessToken)
check('登出后旧 Access 被 401', afterLogout.status === 401)
const rfAfterLogout = await api('POST', '/auth/refresh', { refreshToken })
check('登出后 Refresh 已删除', rfAfterLogout.status === 401)

console.log(`\n结果: ${passed} 通过 / ${failed} 失败`)
process.exit(failed > 0 ? 1 : 0)
