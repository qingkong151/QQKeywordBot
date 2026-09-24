package com.qqbot.keywordbot.napcat

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NapCat 管理器 — Root + Chroot 方案。
 *
 * 参考 NapCat-Installer，使用 Android 设备的 root 权限：
 *   su + mount bind + chroot 真正的 Ubuntu 用户空间。
 *
 * 与之前的 proot 用户态容器方案对比：
 *   proot:   沙箱内手动解压 rootfs → 符号链接问题 / apt 装不了
 *   chroot:  真正的 mount bind + chroot → apt 原生工作，没有用户态模拟开销
 *
 * 安装目录：/data/adb/napcat/
 *   rootfs/    — ubuntu-base-arm64
 *   napcat/    — NapCat Shell
 *   .setup-done — 安装完成标记
 */
@Singleton
class NapCatManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(NapCatState.STOPPED)
    val state: StateFlow<NapCatState> = _state.asStateFlow()

    private val _logs = MutableStateFlow<List<String>>(emptyList())
    val logs: StateFlow<List<String>> = _logs.asStateFlow()

    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    private val _qqDebReady = MutableStateFlow(false)
    val qqDebReady: StateFlow<Boolean> = _qqDebReady.asStateFlow()

    /** 是否有 root 权限 */
    private val _hasRoot = MutableStateFlow<Boolean?>(null)
    val hasRoot: StateFlow<Boolean?> = _hasRoot.asStateFlow()

    /** 安装状态：null=未检查, false=未安装, true=已安装 */
    private val _installed = MutableStateFlow<Boolean?>(null)
    val installed: StateFlow<Boolean?> = _installed.asStateFlow()

    private var napcatLogJob: kotlinx.coroutines.Job? = null

    // root 安装目录（Magisk/KernelSU 可写）
    private val installDir = "/data/adb/napcat"
    private val scriptPath = "$installDir/install.sh"
    private val logPath = "$installDir/napcat.log"
    private val pidPath = "$installDir/.setup-done"

    // App 私有目录下的 QQ.deb（有则优先用）
    private val qqDebFile: File by lazy { File(context.filesDir, "qq.deb") }

    /** 外部资源目录 */
    private val externalResDir: File by lazy {
        File("/storage/emulated/0/QQKeywordBot").apply { mkdirs() }
    }

    // ============================================================
    //  对外接口（保持与之前一致）
    // ============================================================

    /** 请求 Root 权限（触发 Magisk/KernelSU 弹窗） */
    suspend fun requestRoot(): Boolean = withContext(Dispatchers.IO) {
        log("请求 Root 权限...")
        val output = runSu("id", timeoutSec = 10)
        val ok = output.any { it.contains("uid=0") }
        _hasRoot.value = ok
        if (ok) {
            log("✅ Root 权限可用")
            // 顺便检查安装状态
            _installed.value = runSu("test -f $pidPath && echo yes || echo no")
                .firstOrNull()?.trim() == "yes"
        } else {
            log("❌ 未获取 Root 权限，请在 Magisk/KernelSU 中授权本应用")
            log("授权后请重新点击启动按钮")
        }
        ok
    }

    /** 检测环境状态 */
    fun checkEnvironment() {
        scope.launch {
            if (_hasRoot.value != true) return@launch
            _installed.value = runSu("test -f $pidPath && echo yes || echo no")
                .firstOrNull()?.trim() == "yes"
        }
    }

    /** 安装 NapCat（suspend，会阻塞到安装完成或失败） */
    suspend fun install() = withContext(Dispatchers.IO) {
        if (_hasRoot.value != true) {
            if (!requestRoot()) {
                _state.value = NapCatState.ERROR
                return@withContext
            }
        }
        _state.value = NapCatState.STARTING

        // 1. 部署安装脚本
        deployInstallScript()

        // 2. 如果用户上传了 QQ.deb，复制过去给脚本用
        runCatching {
            if (qqDebFile.exists() && qqDebFile.length() > 1_000_000) {
                runSu("cp ${qqDebFile.absolutePath} $installDir/QQ.deb")
                log("已复制 QQ.deb 到安装目录")
            }
        }

        // 3. 执行安装
        log("开始安装（首次需要下载 ubuntu-base + apt install 依赖，大约 5-10 分钟）...")
        runSu("bash $scriptPath install", streaming = true)

        val ok = runSu("test -f $pidPath && echo yes || echo no").firstOrNull()?.trim() == "yes"
        if (ok) {
            log("✅ 安装完成")
            _installed.value = true
            _state.value = NapCatState.STOPPED
        } else {
            log("❌ 安装失败，请查看日志")
            _state.value = NapCatState.ERROR
        }
    }

    /** 启动 NapCat（首次自动执行 install） */
    fun start(qqAccount: String? = null) {
        if (_state.value == NapCatState.RUNNING) return
        _state.value = NapCatState.STARTING

        scope.launch {
            // 先获取 Root（会触发 Magisk 弹窗）
            if (_hasRoot.value != true) {
                if (!requestRoot()) {
                    _state.value = NapCatState.ERROR
                    return@launch
                }
            }

            // 检查是否已安装
            val isSetupDone = runSu("test -f $pidPath && echo yes || echo no")
                .firstOrNull()?.trim() == "yes"

            if (!isSetupDone) {
                log("首次运行，自动安装...")
                _installed.value = false
                install()  // suspend，会阻塞到安装完成
                if (_installed.value != true) {
                    log("❌ 自动安装失败")
                    _state.value = NapCatState.ERROR
                    return@launch
                }
            }

            // 部署脚本（确保脚本是最新版）
            deployInstallScript()

            // 启动 NapCat
            log("启动 NapCat...")
            val output = runSu("bash $scriptPath start", timeoutSec = 15)
            output.forEach { log(it) }

            // 轮询日志文件来跟踪状态
            delay(2000)
            val running = runSu("pgrep -f napcat.mjs >/dev/null && echo yes || echo no")
                .firstOrNull()?.trim() == "yes"
            if (running) {
                _state.value = NapCatState.RUNNING
                log("✅ NapCat 已启动")
                startLogPolling()
            } else {
                log("❌ NapCat 启动失败")
                _state.value = NapCatState.ERROR
            }
        }
    }

    fun stop() {
        scope.launch {
            runSu("bash $scriptPath stop")
            napcatLogJob?.cancel()
            napcatLogJob = null
            _qrCodeUrl.value = null
            log("NapCat 已停止")
            _state.value = NapCatState.STOPPED
        }
    }

    fun clearQrCode() { _qrCodeUrl.value = null }

    fun importQqDeb(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(qqDebFile).use { output -> input.copyTo(output) }
            } ?: return false
            if (qqDebFile.length() < 1_000_000) { qqDebFile.delete(); return false }
            _qqDebReady.value = true
            log("QQ.deb 导入成功（${qqDebFile.length() / 1_000_000} MB）")
            true
        }.getOrElse { false }
    }

    fun deleteQqDeb() {
        if (qqDebFile.exists()) {
            qqDebFile.delete()
            _qqDebReady.value = false
            log("已删除 QQ.deb")
        }
    }

    fun refreshQqDebStatus() {
        _qqDebReady.value = qqDebFile.exists() && qqDebFile.length() > 1_000_000
    }

    fun destroy() { stop(); scope.cancel() }

    enum class NapCatState { STOPPED, STARTING, RUNNING, ERROR }

    // ============================================================
    //  内部实现
    // ============================================================

    /** 通过 su 执行 shell 命令，返回输出行列表 */
    private suspend fun runSu(
        cmd: String,
        timeoutSec: Int = 300,
        streaming: Boolean = false
    ): List<String> = withContext(Dispatchers.IO) {
        val fullCmd = "su -c '$cmd'"
        val process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
        val output = mutableListOf<String>()

        val readerJob = scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.add(line)
                    if (streaming) {
                        log(line)
                        parseQrCode(line)
                    }
                }
            }
        }
        val errJob = scope.launch {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    output.add(line)
                    if (streaming) log(line)
                }
            }
        }

        process.waitFor()
        readerJob.cancel()
        errJob.cancel()
        output
    }

    /** 把 install-root.sh 从 assets 推送到 /data/adb/napcat/install.sh */
    private suspend fun deployInstallScript() = withContext(Dispatchers.IO) {
        runSu("mkdir -p $installDir")

        // 从 assets 读脚本内容，通过 su 写入
        val content = context.assets.open("install-root.sh").bufferedReader().use { it.readText() }
        // 用 base64 避免 su -c 中的转义问题
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        runSu("echo $b64 | base64 -d > $scriptPath && chmod +x $scriptPath")

        if (qqDebFile.exists() && qqDebFile.length() > 1_000_000) {
            runCatching { runSu("cp ${qqDebFile.absolutePath} $installDir/QQ.deb") }
        }
    }

    /** 轮询 napcat.log 日志文件 */
    private fun startLogPolling() {
        napcatLogJob = scope.launch {
            var lastOffset = 0
            while (isActive) {
                val content = runSu("cat $logPath 2>/dev/null || true")
                    .joinToString("\n")
                if (content.isNotEmpty() && content.length > lastOffset) {
                    val newPart = content.substring(lastOffset)
                    newPart.lines().filter { it.isNotBlank() }.forEach {
                        parseQrCode(it)
                        if (it.contains("NapCat") || it.contains("napcat") ||
                            it.contains("ERROR") || it.contains("error") ||
                            it.contains("二维码") || it.contains("qrcode") ||
                            it.contains("启动") || it.contains("login")) {
                            log(it.trim())
                        }
                    }
                    lastOffset = content.length.coerceAtMost(10_000_000)
                }
                delay(2000)
            }
        }
    }

    private fun parseQrCode(line: String) {
        val urlRegex = Regex("https?://[^\\s]+qrcode[^\\s]*", RegexOption.IGNORE_CASE)
        urlRegex.find(line)?.let {
            _qrCodeUrl.value = it.value
        }
    }

    private fun log(msg: String) {
        scope.launch {
            val current = _logs.value.toMutableList()
            current.add(msg)
            if (current.size > 500) current.removeAll(current.take(current.size - 500))
            _logs.value = current
        }
    }
}

// install-root.sh 已移到 assets/install-root.sh
