package com.qqbot.keywordbot.napcat

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * NapCat 容器管理器。
 *
 * 方案：在 Android 手机内通过 proot 运行一个 Ubuntu rootfs，
 * 在其中运行 NapCatQQ（Node.js 实现）。App 通过本地 WS 与其通信。
 *
 * rootfs 与 proot 二进制首次启动时从 assets 解压。
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

    // 扫码登录二维码 URL（NapCat 日志中解析）
    private val _qrCodeUrl = MutableStateFlow<String?>(null)
    val qrCodeUrl: StateFlow<String?> = _qrCodeUrl.asStateFlow()

    // 用户上传的 QQ.deb 文件状态
    private val _qqDebReady = MutableStateFlow(false)
    val qqDebReady: StateFlow<Boolean> = _qqDebReady.asStateFlow()

    // 用户上传的 QQ.deb 存储路径（App 私有目录）
    private val qqDebFile: File by lazy {
        File(context.filesDir, "qq.deb")
    }

    private var containerProcess: Process? = null
    private var logJob: kotlinx.coroutines.Job? = null

    // proot rootfs 所在目录
    private val rootfsDir: File by lazy {
        File(context.filesDir, "ubuntu-rootfs").apply { mkdirs() }
    }

    // proot 二进制路径（jniLibs 解压到 nativeLibraryDir，可执行）
    private val prootBin: File by lazy {
        File(context.applicationInfo.nativeLibraryDir, "libproot.so")
    }

    // proot 依赖库 libtalloc（jniLibs 中的 libtalloc.so）
    private val libtallocPath: String by lazy {
        File(context.applicationInfo.nativeLibraryDir, "libtalloc.so").absolutePath
    }

    // proot 依赖库 libandroid-shmem（部分设备系统不带，需自带）
    private val libandroidShmemPath: String by lazy {
        File(context.applicationInfo.nativeLibraryDir, "libandroid-shmem.so").absolutePath
    }

    // NapCat 安装目录（在 rootfs 内）
    private val napcatDir: File by lazy {
        File(rootfsDir, "opt/napcat")
    }

    // 外部资源目录：用户可将 proot 二进制和 ubuntu-rootfs.zip 放在此目录
    private val externalResDir: File by lazy {
        File("/storage/emulated/0/QQKeywordBot").apply { mkdirs() }
    }

    /**
     * 启动 NapCat。
     */
    fun start(qqAccount: String? = null) {
        if (_state.value == NapCatState.RUNNING) return
        _state.value = NapCatState.STARTING

        scope.launch {
            runCatching {
                refreshQqDebStatus()
                ensureEnvironment()
                val cmd = buildStartCommand(qqAccount)
                log("启动命令: ${cmd.joinToString(" ")}")

                // proot 需要可写的临时目录（Termux 版硬编码了 Termux 路径，需覆盖）
                val prootTmpDir = File(context.filesDir, "proot-tmp").apply { mkdirs() }
                val prootLoader = File(context.applicationInfo.nativeLibraryDir, "libproot-loader.so")
                val pb = ProcessBuilder(cmd)
                    .directory(rootfsDir)
                    .redirectErrorStream(true)
                pb.environment().apply {
                    put("HOME", "/root")
                    put("PATH", "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin")
                    put("TMPDIR", "/tmp")
                    // proot 依赖 libtalloc.so 和 libandroid-shmem.so，都在 nativeLibraryDir
                    put("LD_LIBRARY_PATH", context.applicationInfo.nativeLibraryDir)
                    // Termux 版 proot 硬编码了 Termux 路径，需覆盖
                    put("PROOT_TMP_DIR", prootTmpDir.absolutePath)
                    put("PROOT_LOADER", prootLoader.absolutePath)
                    // 告诉 NapCat QQ 安装位置（proot 容器内路径）
                    // QQ 3.2.20 结构：/opt/QQ/resources/app/{package.json,wrapper.node,major.node}
                    put("NAPCAT_QQ_PACKAGE_INFO_PATH", "/opt/QQ/resources/app/package.json")
                    put("NAPCAT_WRAPPER_PATH", "/opt/QQ/resources/app/wrapper.node")
                }
                val process = pb.start()
                containerProcess = process

                logJob = launch { collectLogs(process) }
                _state.value = NapCatState.RUNNING
            }.onFailure {
                log("启动失败: ${it.message}")
                _state.value = NapCatState.ERROR
            }
        }
    }

    fun stop() {
        scope.launch {
            runCatching {
                containerProcess?.destroyForcibly()?.waitFor()
                logJob?.cancel()
                log("NapCat 已停止")
            }
            containerProcess = null
            _qrCodeUrl.value = null
            _state.value = NapCatState.STOPPED
        }
    }

    fun clearQrCode() {
        _qrCodeUrl.value = null
    }

    /**
     * 将 first-run.sh 导出到外部存储，方便用户查看或手动执行。
     */
    fun exportSetupScript(): String? {
        return runCatching {
            val target = File(externalResDir, "first-run.sh")
            context.assets.open("first-run.sh").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.setExecutable(true)
            log("脚本已导出到: ${target.absolutePath}")
            target.absolutePath
        }.getOrNull()
    }

    /**
     * 导入用户选择的 QQ.deb 文件（从 ContentResolver 复制到 App 私有目录）。
     * 导入后 first-run.sh 会优先使用此文件，无需联网下载。
     */
    fun importQqDeb(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(qqDebFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return false
            val size = qqDebFile.length()
            if (size < 1024 * 1024) {  // 小于 1MB 视为无效
                qqDebFile.delete()
                log("QQ.deb 文件过小（${size} bytes），已删除")
                return false
            }
            _qqDebReady.value = true
            log("QQ.deb 导入成功（${size / 1024 / 1024} MB），将在启动 NapCat 时使用")
            true
        }.getOrElse {
            log("QQ.deb 导入失败: ${it.message}")
            false
        }
    }

    /** 删除已导入的 QQ.deb */
    fun deleteQqDeb() {
        if (qqDebFile.exists()) {
            qqDebFile.delete()
            _qqDebReady.value = false
            log("已删除 QQ.deb")
        }
    }

    /** 刷新 QQ.deb 状态（启动时调用） */
    fun refreshQqDebStatus() {
        _qqDebReady.value = qqDebFile.exists() && qqDebFile.length() > 1024 * 1024
    }

    // ---------- 内部 ----------

    private fun buildStartCommand(qqAccount: String?): List<String> {
        // 容器方案：首次运行 first-run.sh（apt install + 下载 QQ/NapCat），
        // 之后直接启动 NapCat。first-run.sh 成功后创建 .setup-done。
        val innerScript = buildString {
            append("if [ ! -f /opt/napcat/.setup-done ]; then ")
            append("bash /opt/napcat/first-run.sh || { echo 'Setup failed'; exit 1; }; ")
            append("fi; ")
            append("cd /opt/napcat && ")
            append("GNUTLS=\$(find /usr/lib -name libgnutls.so.30 2>/dev/null | head -1) && ")
            append("[ -n \"\$GNUTLS\" ] && export LD_PRELOAD=\$GNUTLS; ")
            append("node napcat.mjs")
            if (!qqAccount.isNullOrBlank()) append(" -q $qqAccount")
            append(" webui")
        }
        // 绑定可写目录到 /tmp 和 /var/tmp，解决 proot 下 apt-key/mktemp 无法创建临时文件的问题
        val tmpDir = File(context.filesDir, "tmp").apply { mkdirs() }
        val varTmpDir = File(context.filesDir, "vartmp").apply { mkdirs() }
        val cmd = mutableListOf(
            prootBin.absolutePath,
            "-r", rootfsDir.absolutePath,
            "-0",
            "-w", "/root",
            "-b", "/dev", "-b", "/proc", "-b", "/sys",
            // 根目录 bind mount：替代 /bin, /lib, /sbin 符号链接
            // proot 在部分 Android 文件系统上无法正确解析相对符号链接，
            // 导致 /bin/bash 和动态链接器 /lib/ld-linux-aarch64.so.1 找不到
            "-b", "${rootfsDir.absolutePath}/usr/bin:/bin",
            "-b", "${rootfsDir.absolutePath}/usr/lib:/lib",
            "-b", "${rootfsDir.absolutePath}/usr/sbin:/sbin",
            "-b", "${tmpDir.absolutePath}:/tmp",
            "-b", "${varTmpDir.absolutePath}:/var/tmp",
            "-b", "${context.filesDir.absolutePath}/napcat-data:/opt/napcat/data",
        )
        // 若用户已上传 QQ.deb，绑定到容器内 /opt/qq-user.deb，first-run.sh 优先使用
        if (qqDebFile.exists() && qqDebFile.length() > 1024 * 1024) {
            cmd.add("-b")
            cmd.add("${qqDebFile.absolutePath}:/opt/qq-user.deb")
            log("检测到用户上传的 QQ.deb，将优先使用")
        }
        cmd.add("/bin/bash")
        cmd.add("-c")
        cmd.add(innerScript)
        return cmd
    }

    /**
     * 确保运行环境就绪：proot 二进制 + 干净 Ubuntu rootfs + 首次初始化脚本。
     *
     * 容器方案：rootfs 只包含干净的 ubuntu-base（31MB），
     * 所有依赖（QQ 运行库、QQ 本体、NapCat）由 first-run.sh 在容器内
     * 通过 apt-get install + curl 下载自动安装。
     */
    private suspend fun ensureEnvironment() = withContext(Dispatchers.IO) {
        log("资源目录: ${externalResDir.absolutePath}")
        log("proot 路径: ${prootBin.absolutePath}")

        val ROOTFS_VERSION = "20"
        val markerFile = File(rootfsDir, ".rootfs-ok-v$ROOTFS_VERSION")
        if (!markerFile.exists()) {
            log("rootfs 版本不匹配，重新解压...")
            rootfsDir.deleteRecursively()
            rootfsDir.mkdirs()
            val externalRootfs = File(externalResDir, "ubuntu-rootfs.zip")
            if (externalRootfs.exists()) {
                log("从外部存储解压 Ubuntu rootfs...")
                extractZip(externalRootfs, rootfsDir)
            } else {
                log("从 assets 解压 Ubuntu rootfs（~31MB）...")
                extractAssetZip("ubuntu-rootfs.zip", rootfsDir)
            }
            markerFile.createNewFile()
        }

        // 部署 first-run.sh 和 NapCat.Shell.zip 到容器内 /opt/napcat/
        val napcatOptDir = File(rootfsDir, "opt/napcat").apply { mkdirs() }
        val firstRunScript = File(napcatOptDir, "first-run.sh")
        if (!firstRunScript.exists()) {
            runCatching {
                context.assets.open("first-run.sh").use { input ->
                    FileOutputStream(firstRunScript).use { output -> input.copyTo(output) }
                }
                firstRunScript.setExecutable(true, false)
                log("已部署 first-run.sh")
            }.onFailure { log("部署 first-run.sh 失败: ${it.message}") }
        }
        val napcatZip = File(napcatOptDir, "NapCat.Shell.zip")
        if (!napcatZip.exists()) {
            runCatching {
                context.assets.open("NapCat.Shell.zip").use { input ->
                    FileOutputStream(napcatZip).use { output -> input.copyTo(output) }
                }
                log("已部署 NapCat.Shell.zip")
            }.onFailure { log("部署 NapCat.Shell.zip 失败: ${it.message}") }
        }

        // 修复 Java 解压后丢失的根级符号链接（lib, bin, sbin -> usr/...）
        // proot 的 bind mount 会覆盖这些路径，但部分设备上 bind mount 可能
        // 因目标不是目录而失败，所以这里也创建符号链接作为兜底。
        ensureRootSymlinks()

        log("环境就绪，rootfs: ${rootfsDir.absolutePath}")
    }

    /**
     * 确保根级符号链接存在（/lib, /bin, /sbin -> usr/lib, usr/bin, usr/sbin）。
     * Java ZipInputStream 不保留 zip 里的符号链接属性，解压后这些路径
     * 变成包含目标路径文本的普通文件。删除后重建符号链接。
     */
    private fun ensureRootSymlinks() {
        val links = mapOf(
            "lib" to "usr/lib",
            "bin" to "usr/bin",
            "sbin" to "usr/sbin"
        )
        for ((link, target) in links) {
            val linkFile = File(rootfsDir, link)
            // 如果不是符号链接（是普通文件或不存在但应该是符号链接），重建
            if (linkFile.exists() && !java.nio.file.Files.isSymbolicLink(linkFile.toPath())) {
                linkFile.delete()
            }
            if (!linkFile.exists()) {
                runCatching {
                    android.system.Os.symlink(target, linkFile.absolutePath)
                }
            }
        }
        // 确保 /root 存在（proot -w /root）
        File(rootfsDir, "root").mkdirs()
    }

    private fun extractAsset(assetName: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun extractAssetZip(assetName: String, targetDir: File) {
        targetDir.mkdirs()
        context.assets.open(assetName).use { input ->
            ZipInputStream(input).use { zip ->
                extractZipEntries(zip, targetDir)
            }
        }
    }

    private fun extractZip(zipFile: File, targetDir: File) {
        targetDir.mkdirs()
        java.io.FileInputStream(zipFile).use { input ->
            ZipInputStream(input).use { zip ->
                extractZipEntries(zip, targetDir)
            }
        }
    }

    /**
     * 流式解压 zip，避免 readBytes() 导致 OOM。
     * 符号链接会被当作普通文件解压（Java ZipInputStream 限制），
     * 根级符号链接由 ensureRootSymlinks() 修复，
     * 库级符号链接由容器内 apt-get install 重建。
     */
    private fun extractZipEntries(zip: ZipInputStream, targetDir: File) {
        var entry = zip.nextEntry
        while (entry != null) {
            val entryName = entry.name.removePrefix("./")
            if (entryName.isEmpty()) {
                zip.closeEntry()
                entry = zip.nextEntry
                continue
            }
            val outFile = File(targetDir, entryName)
            if (entry.isDirectory) {
                outFile.mkdirs()
            } else {
                outFile.parentFile?.mkdirs()
                FileOutputStream(outFile).use { output ->
                    zip.copyTo(output)
                }
                outFile.setExecutable(true, false)
                outFile.setReadable(true, false)
                outFile.setWritable(true, false)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    private suspend fun collectLogs(process: Process) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let {
                log(it)
                parseQrCode(it)
            }
        }
    }

    private fun parseQrCode(line: String) {
        val urlRegex = Regex("https?://[^\\s]+qrcode[^\\s]*", RegexOption.IGNORE_CASE)
        val qrTokenRegex = Regex("qrcode.*?(https?://[^\\s]+)", RegexOption.IGNORE_CASE)
        urlRegex.find(line)?.let {
            _qrCodeUrl.value = it.value
            log("检测到登录二维码: ${it.value}")
        } ?: qrTokenRegex.find(line)?.let { match ->
            match.groupValues.getOrNull(1)?.let { url ->
                _qrCodeUrl.value = url
                log("检测到登录二维码: $url")
            }
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

    enum class NapCatState { STOPPED, STARTING, RUNNING, ERROR }

    fun destroy() {
        stop()
        scope.cancel()
    }
}
