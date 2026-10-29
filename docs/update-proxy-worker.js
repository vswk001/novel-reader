/**
 * 墨阅检查更新 · Cloudflare Worker 反代（自建备用方案）
 *
 * 用法：Cloudflare 控制台 → Workers → Create Worker → 粘贴本文件 → Deploy。
 * 然后构建注入：
 *   ./gradlew :app:assembleRelease \
 *     -PupdateFeedUrl=https://<你的 worker域名>/https://api.github.com/repos/vswk001/novel-reader/releases/latest
 *
 * App 会自动识别「前缀 + 完整URL」形态，并把同一前缀套用到 APK 下载直链。
 * 免费额度 10 万次/天，对更新检查场景绰绰有余。
 */
export default {
  async fetch(request) {
    const u = new URL(request.url)
    // 路径即目标地址：/https://api.github.com/...（保留 query）
    const target = decodeURIComponent(u.pathname.slice(1)) + u.search
    if (!target.startsWith('http')) {
      return new Response('usage: /<full-url>', { status: 400 })
    }
    const upstream = await fetch(target, {
      headers: {
        'User-Agent': 'MoYue-Reader',
        'Accept': 'application/vnd.github+json',
      },
      redirect: 'follow', // Release 资产会 302 到 objects.githubusercontent.com
    })
    const resp = new Response(upstream.body, upstream)
    resp.headers.set('Access-Control-Allow-Origin', '*')
    return resp
  },
}
