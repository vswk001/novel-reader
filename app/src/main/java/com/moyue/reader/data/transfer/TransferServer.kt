package com.moyue.reader.data.transfer

import com.moyue.reader.data.importer.BookImporter
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.InetAddress
import java.net.NetworkInterface
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.UUID

/**
 * Wi-Fi 局域网传书：手机内嵌 HTTP 服务，浏览器打开地址 + 配对码后 PUT 上传文件。
 * 安全：一次性配对码（HttpOnly cookie token）、仅 Wi-Fi 接口、15 分钟无活动自动关闭。
 */
class TransferServer(
    private val cacheDir: File,
    /** 导入回调（生产为 BookImporter.importFile；测试注入桩），在 HTTP 线程 runBlocking 执行 */
    private val importer: suspend (file: File, displayName: String, space: Int) -> BookImporter.ImportResult
) {
    data class Event(val time: Long, val fileName: String, val ok: Boolean, val message: String)

    data class State(
        val running: Boolean = false,
        /** 可访问地址（每个网卡接口一个） */
        val urls: List<String> = emptyList(),
        val pairCode: String = "",
        /** 无活动自动关闭剩余秒数 */
        val secondsLeft: Int = 0,
        val events: List<Event> = emptyList(),
        val space: Int = 0
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    private var server: HttpServer? = null
    private var countdownJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** 本会话有效 token；null = 服务未运行 */
    @Volatile
    private var token: String? = null

    @Volatile
    private var pairFails = 0

    fun start(space: Int) {
        if (server != null) return
        val code = String.format(Locale.US, "%04d", SecureRandom().nextInt(10000))
        val newToken = UUID.randomUUID().toString().replace("-", "")
        val addresses = wifiAddresses()
        if (addresses.isEmpty()) {
            _state.update { it.copy(running = false, urls = emptyList(), pairCode = "") }
            return
        }
        val bind = addresses.first()
        val s = try {
            HttpServer(bind.hostAddress, 0, newToken, code, space).also { it.start() }
        } catch (e: Exception) {
            _state.update { it.copy(running = false, events = it.events + Event(System.currentTimeMillis(), "", false, e.message ?: "start failed")) }
            return
        }
        token = newToken
        server = s
        val port = s.listeningPort
        _state.update {
            State(
                running = true,
                urls = addresses.map { "http://${it.hostAddress}:$port" },
                pairCode = code,
                secondsLeft = TIMEOUT_SEC,
                space = space
            )
        }
        countdownJob = scope.launch {
            var left = TIMEOUT_SEC
            while (isActive && left > 0) {
                delay(1_000)
                val reset = s.touchAndTake()
                if (reset) left = TIMEOUT_SEC else left--
                _state.update { it.copy(secondsLeft = left) }
            }
            if (left <= 0) stop(idle = true)
        }
    }

    fun stop(idle: Boolean = false) {
        countdownJob?.cancel()
        countdownJob = null
        runCatching { server?.stop() }
        server = null
        token = null
        _state.update { st ->
            State(
                running = false,
                events = if (idle) st.events else emptyList()
            )
        }
    }

    /** 测试钩子：绑回环地址启动（跳过 Wi-Fi 地址发现），返回 (baseUrl, 配对码) */
    fun startLoopbackForTest(): Pair<String, String> {
        val code = String.format(Locale.US, "%04d", SecureRandom().nextInt(10000))
        val newToken = UUID.randomUUID().toString().replace("-", "")
        val s = HttpServer("127.0.0.1", 0, newToken, code, 0).also { it.start() }
        token = newToken
        server = s
        val base = "http://127.0.0.1:${s.listeningPort}"
        _state.update {
            State(running = true, urls = listOf(base), pairCode = code, secondsLeft = TIMEOUT_SEC)
        }
        return base to code
    }

    /** 测试钩子：当前会话 token */
    fun testToken(): String? = token

    /** Wi-Fi/热点等候选地址（排除回环与移动数据常见接口名） */
    private fun wifiAddresses(): List<InetAddress> {
        val all = runCatching {
            Collections.list(NetworkInterface.getNetworkInterfaces())
        }.getOrNull().orEmpty()
        val preferred = mutableListOf<InetAddress>()
        val fallback = mutableListOf<InetAddress>()
        for (nif in all) {
            if (!nif.isUp || nif.isLoopback) continue
            val name = nif.name.lowercase(Locale.US)
            val mobile = name.startsWith("rmnet") || name.startsWith("ccmni") ||
                name.startsWith("p2p") || name.startsWith("dummy")
            for (addr in Collections.list(nif.inetAddresses)) {
                if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                    if (mobile) continue
                    if (name.contains("wlan") || name.contains("sw") || name.contains("ap") ||
                        name.contains("hotspot") || name.contains("eth")
                    ) preferred += addr else fallback += addr
                }
            }
        }
        return (preferred + fallback).distinct()
    }

    private companion object {
        const val TIMEOUT_SEC = 15 * 60
        const val MAX_BYTES = 256L * 1024 * 1024
        val ALLOWED_EXT = setOf("txt", "log", "md", "epub")
    }

    /** 活跃即重置倒计时 */
    @Volatile
    private var lastTouch = 0L

    private inner class HttpServer(
        host: String,
        port: Int,
        private val sessionToken: String,
        private val code: String,
        private val space: Int
    ) : NanoHTTPD(host, port) {

        fun touchAndTake(): Boolean = synchronized(this) {
            System.currentTimeMillis() - lastTouch < 2_000
        }

        private fun touch() = synchronized(this) { lastTouch = System.currentTimeMillis() }

        private fun authorized(session: IHTTPSession): Boolean {
            val cookies = session.headers["cookie"] ?: return false
            return cookies.split(';').any { it.trim() == "moyue_token=$sessionToken" }
        }

        override fun serve(session: IHTTPSession): Response {
            touch()
            val path = session.uri
            return when {
                path == "/" || path.isEmpty() -> newFixedLengthResponse(Response.Status.OK, "text/html; charset=utf-8", PAGE)

                path == "/pair" && session.method == Method.POST -> {
                    // NanoHTTPD 2.3.1：POST 表单参数写入 parseBody 传入的 map（非 session.parameters）
                    val form = HashMap<String, String>()
                    runCatching { session.parseBody(form) }
                    val input = form["code"]
                        ?: session.parameters["code"]?.firstOrNull()
                        ?: session.parms["code"]
                        ?: ""
                    if (input == code) {
                        val r = newFixedLengthResponse(Response.Status.OK, "application/json", "{\"ok\":true}")
                        r.addHeader("Set-Cookie", "moyue_token=$sessionToken; HttpOnly; SameSite=Strict")
                        r
                    } else {
                        pairFails++
                        if (pairFails > 10) closeAllConnections()
                        newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\"ok\":false}")
                    }
                }

                // multipart POST：NanoHTTPD 官方上传通道，文件自动写入临时目录（不进内存）
                path == "/upload" && session.method == Method.POST -> {
                    if (!authorized(session)) {
                        return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{\"ok\":false,\"msg\":\"unauthorized\"}")
                    }
                    handleUpload(session)
                }

                else -> newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "404")
            }
        }

        private fun handleUpload(session: IHTTPSession): Response {
            val name = session.parameters["name"]?.firstOrNull()?.let {
                it.substringAfterLast('/').substringAfterLast('\\').take(120)
            } ?: "file"
            val ext = name.substringAfterLast('.', "").lowercase(Locale.US)
            if (ext !in ALLOWED_EXT) {
                return newFixedLengthResponse(
                    Response.Status.NOT_ACCEPTABLE, "application/json",
                    "{\"ok\":false,\"msg\":\"unsupported: .$ext\"}"
                )
            }
            val declared = session.headers["content-length"]?.toLongOrNull() ?: -1L
            if (declared > MAX_BYTES + 1024) {
                return newFixedLengthResponse(
                    Response.Status.PAYLOAD_TOO_LARGE, "application/json",
                    "{\"ok\":false,\"msg\":\"too large\"}"
                )
            }
            // multipart 解析：文件由 NanoHTTPD 写入临时文件（不进内存），files["file"] 为路径
            val files = HashMap<String, String>()
            return try {
                session.parseBody(files)
                val tmp = File(
                    files["file"] ?: throw IllegalStateException("no file part")
                )
                if (!tmp.exists() || tmp.length() == 0L) throw IllegalStateException("empty file")
                if (tmp.length() > MAX_BYTES) throw SecurityException("too large")
                val result = kotlinx.coroutines.runBlocking { importer(tmp, name, space) }
                _state.update {
                    it.copy(
                        events = (it.events + Event(
                            System.currentTimeMillis(), name, result.ok, result.message
                        )).takeLast(50)
                    )
                }
                newFixedLengthResponse(
                    Response.Status.OK, "application/json",
                    "{\"ok\":" + result.ok + ",\"msg\":\"" + result.message
                        .replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
                ).also { it.addHeader("Connection", "close") }
            } catch (e: Exception) {
                newFixedLengthResponse(
                    Response.Status.INTERNAL_ERROR, "application/json",
                    "{\"ok\":false,\"msg\":\"upload failed\"}"
                ).also { it.addHeader("Connection", "close") }
            } finally {
                files["file"]?.let { runCatching { File(it).delete() } }
            }
        }
    }

    /** 内置上传页（原生 HTML/JS，中英按浏览器语言切换） */
    private val PAGE = """
<!doctype html>
<html><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>MoYue · Wi-Fi Transfer</title>
<style>
 body{font-family:system-ui,-apple-system,"Segoe UI",sans-serif;background:#1a237e14;min-height:100vh;margin:0;display:flex;align-items:center;justify-content:center}
 .card{background:#fff;border-radius:18px;box-shadow:0 8px 40px #1a237e22;padding:34px 38px;width:min(92vw,460px)}
 h1{font-size:19px;margin:0 0 6px;color:#283593}
 p.sub{color:#666;font-size:13px;margin:0 0 20px}
 input[type=text]{font-size:22px;letter-spacing:12px;text-align:center;width:180px;padding:10px;border:2px solid #c5cae9;border-radius:12px;outline:none}
 input[type=text]:focus{border-color:#3949ab}
 button{background:#3949ab;color:#fff;border:0;border-radius:12px;padding:12px 22px;font-size:15px;cursor:pointer;margin-top:14px}
 button:disabled{background:#9fa8da}
 .files{margin:18px 0 6px}
 .row{display:flex;align-items:center;gap:10px;padding:9px 0;border-bottom:1px solid #e8eaf6;font-size:14px}
 .name{flex:1;word-break:break-all}
 .ok{color:#2e7d32}.fail{color:#c62828}
 .bar{height:5px;border-radius:3px;background:#e8eaf6;overflow:hidden;width:100%}
 .bar>i{display:block;height:100%;background:#3949ab;width:0;transition:width .15s}
 .hide{display:none}
 .hint{font-size:12px;color:#999;margin-top:14px;line-height:1.6}
</style></head><body>
<div class="card">
 <h1 id="t"></h1><p class="sub" id="s"></p>
 <div id="pairBox">
   <input id="code" type="text" maxlength="4" inputmode="numeric" placeholder="0000">
   <br><button id="pairBtn" onclick="pair()"></button>
   <p id="pairErr" style="color:#c62828;font-size:13px" class="hide"></p>
 </div>
 <div id="upBox" class="hide">
   <button id="pick" onclick="document.getElementById('f').click()"></button>
   <input id="f" type="file" multiple accept=".txt,.log,.md,.epub" style="display:none" onchange="uploadAll(this.files)">
   <div class="files" id="list"></div>
 </div>
 <p class="hint" id="h"></p>
</div>
<script>
var zh=(navigator.language||'').toLowerCase().indexOf('zh')===0;
var L=zh?{t:'墨阅 · Wi-Fi 传书',s:'输入手机上显示的配对码',b:'验证',e:'配对码不正确',pick:'选择文件（可多选）',h:'支持 txt / log / md / epub，单文件最大 256MB。传输过程中请保持手机页面打开。'}
 :{t:'MoYue · Wi-Fi Transfer',s:'Enter the pairing code shown on your phone',b:'Verify',e:'Wrong pairing code',pick:'Choose files (multiple)',h:'Supports txt / log / md / epub, max 256MB each. Keep the phone screen on while transferring.'};
t.textContent=L.t;s.textContent=L.s;pairBtn.textContent=L.b;pick.textContent=L.pick;h.textContent=L.h;
function pair(){
 var c=document.getElementById('code').value.trim();
 fetch('/pair',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:'code='+encodeURIComponent(c)})
  .then(function(r){return r.json()})
  .then(function(j){if(j.ok){document.getElementById('pairBox').classList.add('hide');document.getElementById('upBox').classList.remove('hide');}else{var e=document.getElementById('pairErr');e.textContent=L.e;e.classList.remove('hide');}})
  .catch(function(){var e=document.getElementById('pairErr');e.textContent='network error';e.classList.remove('hide');});
}
function uploadAll(files){
 for(var i=0;i<files.length;i++)upload(files[i],files.length>1?(i+1)+'/'+files.length+' ':'');
}
function upload(f,tag){
 var row=document.createElement('div');row.className='row';
 row.innerHTML='<div class="name" style="flex:1"><div>'+tag+f.name+'</div><div class="bar"><i></i></div></div><span class="st">…</span>';
 document.getElementById('list').appendChild(row);
 var bar=row.querySelector('i'),st=row.querySelector('.st');
 var x=new XMLHttpRequest();
 x.open('POST','/upload?name='+encodeURIComponent(f.name));
 var fd=new FormData();fd.append('file',f);
 x.upload.onprogress=function(ev){if(ev.lengthComputable)bar.style.width=Math.round(ev.loaded/ev.total*100)+'%';};
 x.onload=function(){
   var m='';try{m=(JSON.parse(x.responseText)).msg||'';}catch(e){}
   if(x.status===200){st.textContent='✓';st.className='st ok';bar.style.width='100%';row.title=m;}
   else{st.textContent='✗';st.className='st fail';row.title=m||('HTTP '+x.status);}
 };
 x.onerror=function(){st.textContent='✗';st.className='st fail';};
 x.send(fd);
}
</script></body></html>
"""
}
