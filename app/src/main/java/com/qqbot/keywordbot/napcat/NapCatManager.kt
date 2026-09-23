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
     * 将 assets 中的 setup-napcat.sh 导出到外部存储，
     * 方便用户在 Termux 中执行环境初始化。
     */
    fun exportSetupScript(): String? {
        return runCatching {
            val target = File(externalResDir, "setup-napcat.sh")
            context.assets.open("setup-napcat.sh").use { input ->
                FileOutputStream(target).use { output -> input.copyTo(output) }
            }
            target.setExecutable(true)
            log("setup 脚本已导出到: ${target.absolutePath}")
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
        // 首次运行脚本会：修复 dpkg → apt 装库 → 下载 QQ → 启动 NapCat
        // 已初始化则直接启动 NapCat
        // .setup-done 由 first-run.sh 内部在 QQ 安装成功后才创建，
        // 这里不再额外 touch，避免首次失败后跳过重试。
        val innerScript = buildString {
            append("if [ ! -f /opt/napcat/.setup-done ] || [ ! -f /opt/QQ/resources/app/package.json ]; then ")
            append("rm -f /opt/napcat/.setup-done; ")
            append("bash /opt/napcat/first-run.sh; ")
            append("else ")
            // 确保符号链接存在（升级 APK 后可能跳过 first-run.sh）
            append("mkdir -p /usr/local/bin && ln -sfn /opt/QQ/resources/app /usr/local/bin/resources/app; ")
            append("cd /opt/napcat && ")
            append("GNUTLS=\$(find /usr/lib -name libgnutls.so.30 2>/dev/null | head -1) && ")
            append("[ -n \"\$GNUTLS\" ] && export LD_PRELOAD=\$GNUTLS; ")
            append("node napcat.mjs")
            if (!qqAccount.isNullOrBlank()) append(" -q $qqAccount")
            append(" webui; ")
            append("fi")
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
     * 确保运行环境就绪：proot 二进制 + Ubuntu rootfs + NapCat。
     * 优先从外部存储目录 /storage/emulated/0/QQKeywordBot/ 加载，
     * 其次从 assets 解压。
     */
    private suspend fun ensureEnvironment() = withContext(Dispatchers.IO) {
        log("资源目录: ${externalResDir.absolutePath}")

        // 1. proot 二进制已通过 jniLibs 打包，自动解压到 nativeLibraryDir（可执行）
        log("proot 路径: ${prootBin.absolutePath}")

        // 2. 准备 Ubuntu rootfs（用版本标记确保权限/符号链接正确）
        // 版本号变更时强制重新解压（更新 first-run.sh 等内置脚本）
        val ROOTFS_VERSION = "12"
        val markerFile = File(rootfsDir, ".rootfs-ok-v$ROOTFS_VERSION")
        if (!markerFile.exists()) {
            log("rootfs 版本不匹配，重新解压...")
            // 清理旧的不完整解压
            rootfsDir.deleteRecursively()
            rootfsDir.mkdirs()
            val externalRootfs = File(externalResDir, "ubuntu-rootfs.zip")
            if (externalRootfs.exists()) {
                log("从外部存储解压 Ubuntu rootfs（首次启动较慢，请耐心等待）...")
                extractZip(externalRootfs, rootfsDir)
            } else {
                log("从 assets 解压 Ubuntu rootfs（首次启动较慢，请耐心等待）...")
                extractAssetZip("ubuntu-rootfs.zip", rootfsDir)
            }
            markerFile.createNewFile()
        }

        // 2.5 创建根目录符号链接（Ubuntu 24.04 的 /bin, /lib, /sbin, /lib64 都是符号链接）
        // createRootSymlinks()  // 已由 extractZipEntries 自动处理

        // 3. 确保 NapCat 目录存在
        if (!napcatDir.exists()) {
            napcatDir.mkdirs()
        }

        // 4. 部署 OneBot v11 配置文件（正向 WS 端口 3001，与 OneBotClient 默认一致）
        deployNapcatConfig()

        log("环境就绪，rootfs: ${rootfsDir.absolutePath}")
    }

    /**
     * 将 assets 中的 OneBot 配置文件部署到 napcat-data/config/。
     * napcat-data 目录通过 proot -b 绑定到容器内的 /opt/napcat/data。
     */
    private fun deployNapcatConfig() {
        val configDir = File(context.filesDir, "napcat-data/config").apply { mkdirs() }
        val configFiles = listOf("onebot11.json", "napcat.json")
        for (name in configFiles) {
            val target = File(configDir, name)
            if (!target.exists()) {
                runCatching {
                    context.assets.open(name).use { input ->
                        FileOutputStream(target).use { output -> input.copyTo(output) }
                    }
                    log("已部署配置: $name")
                }.onFailure {
                    log("部署配置 $name 失败: ${it.message}")
                }
            }
        }
    }

    private fun extractAsset(assetName: String, target: File) {
        target.parentFile?.mkdirs()
        context.assets.open(assetName).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * 从 assets/symlinks.txt 读取并创建所有符号链接。
     * 打包时未包含符号链接，需在解压后手动创建。
     */
    private fun createRootSymlinks() {
        var created = 0
        var failed = 0
        val errors = mutableListOf<String>()
        runCatching {
            context.assets.open("symlinks.txt").bufferedReader().useLines { lines ->
                for (line in lines) {
                    val parts = line.split(" -> ", limit = 2)
                    if (parts.size != 2) continue
                    val (linkPath, target) = parts
                    val linkFile = File(rootfsDir, linkPath)
                    // 删除已存在的文件/目录（解压时符号链接被当作普通文件）
                    if (linkFile.exists()) {
                        linkFile.delete()
                    }
                    linkFile.parentFile?.mkdirs()
                    runCatching {
                        android.system.Os.symlink(target, linkFile.absolutePath)
                        created++
                    }.onFailure { e ->
                        failed++
                        if (errors.size < 3) {
                            errors.add("$linkPath -> $target : ${e.message}")
                        }
                    }
                }
            }
            log("已创建 $created 个符号链接，失败 $failed 个")
            if (errors.isNotEmpty()) {
                log("符号链接错误示例: ${errors.joinToString("; ")}")
            }
        }.onFailure {
            log("符号链接创建失败: ${it.message}")
        }

        // 确保 /root 目录存在（proot -w /root 需要）
        val rootHome = File(rootfsDir, "root")
        if (!rootHome.exists()) {
            rootHome.mkdirs()
            log("已创建 /root 目录")
        }

        // 验证关键符号链接
        val binLink = File(rootfsDir, "bin")
        log("/bin 存在: ${binLink.exists()}, 是符号链接: ${binLink.canonicalPath != binLink.absolutePath}")
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
     * 解压 zip 条目，设置可执行权限（rootfs 依赖 /bin/bash 等可执行文件）。
     * 处理符号链接：ZipInputStream 不能识别符号链接，
     * dpkg-deb -x 把链接目标路径（如 "libX11.so.6.4.0"）存入 zip，
     * 解压后用 file.length() 判断——如果很小且内容是已存在的文件名，则创建符号链接。
     */
    private fun extractZipEntries(zip: ZipInputStream, targetDir: File) {
        var entry = zip.nextEntry
        while (entry != null) {
            val entryName = entry.name.removePrefix("rootfs/").removePrefix("./")
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
                // 全部读入，检测是否为符号链接
                val bytes = zip.readBytes()
                val contentStr = String(bytes).trim()
                // 判断条件：内容很短 + 不含换行 + 目标路径存在于同一目录
                val isSymlinkCandidate = bytes.size in 1..128
                    && !contentStr.contains('\n')
                    && !contentStr.contains('\u0000')
                    && contentStr.isNotEmpty()
                if (isSymlinkCandidate) {
                    // 尝试在同一目录找目标文件
                    val sibling = File(outFile.parentFile, contentStr)
                    val resolved = resolveSymlinkTarget(outFile, contentStr, targetDir)
                    if (resolved != null) {
                        // 删掉错误的普通文件，创建真正的符号链接
                        outFile.delete()
                        try {
                            android.system.Os.symlink(resolved, outFile.absolutePath)
                            zip.closeEntry()
                            entry = zip.nextEntry
                            continue
                        } catch (_: Exception) {
                            // 回退：当普通文件处理
                        }
                    }
                }
                // 普通文件 fallback
                FileOutputStream(outFile).use { output ->
                    output.write(bytes)
                }
                outFile.setExecutable(true, false)
                outFile.setReadable(true, false)
                outFile.setWritable(true, false)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    /**
     * 解析符号链接的目标路径。
     * 优先原路径（相对/绝对都查），找不到则在 targetDir 里查实际目标文件位置。
     */
    private fun resolveSymlinkTarget(linkFile: File, rawTarget: String, targetDir: File): String? {
        // 1. 绝对路径直接返回
        if (rawTarget.startsWith("/")) {
            val abs = File(rawTarget)
            if (abs.exists()) return rawTarget
        }
        // 2. 相对路径在同目录查
        val sibling = File(linkFile.parentFile, rawTarget)
        if (sibling.exists()) return rawTarget
        // 3. 在 targetDir 下全局搜索（绝对定位）
        val found = targetDir.walk().firstOrNull {
            it.name == rawTarget && it.absolutePath.startsWith(targetDir.absolutePath)
        }
        if (found != null) {
            // 返回相对于原位置的相对路径
            return try {
                linkFile.parentFile?.toPath()?.relativize(found.toPath())?.toString()
            } catch (_: Exception) {
                rawTarget
            }
        }
        return null
    }

    private suspend fun collectLogs(process: Process) = withContext(Dispatchers.IO) {
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            line?.let {
                log(it)
                // 解析二维码 URL（NapCat 日志中通常包含 qrcode 或 qr 字样的 URL）
                parseQrCode(it)
            }
        }
    }

    private fun parseQrCode(line: String) {
        // NapCat 登录二维码通常以 URL 形式输出，如 https://...qrcode...
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
