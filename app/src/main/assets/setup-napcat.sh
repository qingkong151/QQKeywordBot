#!/usr/bin/env bash
# ============================================================
#  NapCatQQ 在 Termux proot Ubuntu 中的一键修复 & 启动脚本
#  使用方法：
#    1. 把本脚本传到手机，例如 /sdcard/setup-napcat.sh
#    2. 在 Termux 中进入 Ubuntu：  proot-distro login ubuntu
#    3. 复制进去并执行：
#         cp /sdcard/setup-napcat.sh /root/ && chmod +x /root/setup-napcat.sh
#         /root/setup-napcat.sh
# ============================================================
set -e

# ---- 颜色输出 ----
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERR ]${NC} $*"; }

ARCH=$(dpkg --print-architecture)   # 通常是 arm64
NAPCAT_DIR="/opt/napcat"
QQ_DIR="/opt/QQ"
ONEBOT_PORT=3001

# ============================================================
#  第 1 步：修复 dpkg 状态并安装系统依赖
# ============================================================
step1_fix_dpkg_and_install_deps() {
    info "===== 第 1 步：修复 dpkg 并安装系统依赖 ====="

    # 1.1 把残留的 unpacked 状态全部置为 installed（之前手动修过，这里再保险一次）
    local STATUS="/var/lib/dpkg/status"
    if grep -q "Status: install ok unpacked" "$STATUS" 2>/dev/null; then
        warn "发现 unpacked 状态，正在修正为 installed..."
        sed -i 's/Status: install ok unpacked/Status: install ok installed/g' "$STATUS"
    fi
    local remain
    remain=$(grep -c "unpacked" "$STATUS" 2>/dev/null || echo 0)
    info "dpkg status 中 unpacked 剩余数量: $remain"

    # 1.2 尝试恢复 dpkg 配置
    warn "执行 dpkg --configure -a（可能需要一点时间）..."
    dpkg --configure -a 2>/dev/null || warn "dpkg --configure -a 有警告，可忽略"

    # 1.3 apt 修复 + 安装依赖
    warn "执行 apt-get -f install..."
    apt-get -f install -y 2>/dev/null || warn "apt -f install 有警告"

    warn "apt-get update（若网络不通可忽略，走后面的手动 deb 方案）..."
    apt-get update 2>/dev/null || warn "apt update 失败，将使用手动 deb 安装"

    # 1.4 安装 NapCat 运行 QQ(NT) 所需的系统库
    warn "安装运行时依赖..."
    apt-get install -y --no-install-recommends \
        zip unzip jq curl wget ca-certificates \
        xvfb screen xauth procps rpm2cpio cpio \
        libnss3 libgbm1 libglib2.0-0 libatk1.0-0 libatkspi2.0-0 \
        libgtk-3-0 libasound2 libx11-xcb1 libgnutls30 \
        libxss1 libxtst6 libxrandr2 libxcomposite1 libxdamage1 \
        libxrender1 libxi6 libxext6 libxfixes3 libxkbcommon0 \
        libpango-1.0-0 libcairo2 libcups2 libdrm2 libexpat1 \
        libxcb-dri3-0 libxcb1 libdbus-1-3 2>/dev/null \
        || warn "部分包 apt 安装失败，进入手动 deb 补装"

    info "第 1 步完成"
}

# ============================================================
#  第 2 步：手动补装缺失的关键库（apt 失败时的兜底）
# ============================================================
step2_install_missing_libs() {
    info "===== 第 2 步：补装缺失的关键库 ====="

    local DEB_CACHE="/tmp/napcat-debs"
    mkdir -p "$DEB_CACHE"

    # 需要确保存在的关键 .so（QQ NT 运行必须）
    local MUST_HAVE_LIBS=(
        "libX11-xcb.so.1:libx11-xcb1"
        "libgnutls.so.30:libgnutls30"
        "libnss3.so:libnss3"
        "libgbm.so.1:libgbm1"
        "libgtk-3.so.0:libgtk-3-0"
        "libasound.so.2:libasound2"
    )

    local lib pkg deb_name
    for entry in "${MUST_HAVE_LIBS[@]}"; do
        lib="${entry%%:*}"
        pkg="${entry##*:}"
        if ! ldconfig -p 2>/dev/null | grep -q "$lib" && \
           ! find /usr/lib -name "$lib" 2>/dev/null | grep -q .; then
            warn "缺失 $lib，尝试用 apt 安装 $pkg..."
            # 兼容 Ubuntu 24.04+ 的 t64 过渡包名（如 libasound2 → libasound2t64）
            apt-get install -y --no-install-recommends "$pkg" 2>/dev/null \
                || apt-get install -y --no-install-recommends "${pkg}t64" 2>/dev/null \
                || warn "apt 安装 $pkg 失败，尝试下载 deb 手动解压"
            # 兜底：下载 deb 并直接解压到 /
            if ! ldconfig -p 2>/dev/null | grep -q "$lib" && \
               ! find /usr/lib -name "$lib" 2>/dev/null | grep -q .; then
                ( cd "$DEB_CACHE" && apt-get download "$pkg" 2>/dev/null ) \
                    || ( cd "$DEB_CACHE" && apt-get download "${pkg}t64" 2>/dev/null )
                deb_name=$(ls "$DEB_CACHE"/${pkg}*.deb 2>/dev/null | head -1 || true)
                if [ -n "$deb_name" ]; then
                    dpkg-deb -x "$deb_name" /
                    rm -f "$deb_name"
                fi
            fi
        fi
    done

    ldconfig 2>/dev/null || true

    # 验证关键库
    info "关键库检查："
    for entry in "${MUST_HAVE_LIBS[@]}"; do
        lib="${entry%%:*}"
        if ldconfig -p 2>/dev/null | grep -q "$lib" || find /usr/lib -name "$lib" 2>/dev/null | grep -q .; then
            info "  ✓ $lib"
        else
            error "  ✗ $lib 仍缺失（NapCat 可能无法启动）"
        fi
    done

    info "第 2 步完成"
}

# ============================================================
#  第 3 步：配置 NapCat OneBot v11 正向 WS 并启动
# ============================================================
step3_config_and_start_napcat() {
    info "===== 第 3 步：配置并启动 NapCat ====="

    if [ ! -f "$NAPCAT_DIR/napcat.mjs" ]; then
        error "未找到 $NAPCAT_DIR/napcat.mjs"
        error "请先确认 NapCat 已解压到 $NAPCAT_DIR"
        error "（NapCatQQ Shell 版解压后把 napcat.mjs 所在目录放到 /opt/napcat）"
        return 1
    fi

    # 3.1 确认 QQ 资源存在
    if [ ! -d "$QQ_DIR/resources/app/versions" ]; then
        warn "未找到 $QQ_DIR/resources/app/versions"
        warn "NapCat 需要 QQ NT 的 resources。请确认 /opt/QQ 结构完整。"
        warn "若使用 NapCat Shell 版，通常把 QQ 放到 /opt/QQ"
    fi

    # 3.2 确保 data/config 目录存在
    mkdir -p "$NAPCAT_DIR/data/config"

    # 3.3 写入 OneBot v11 配置（正向 WS，端口 3001）
    # 注意：NapCat 的 OneBot 配置文件名是 onebot11_<QQ号>.json
    # 如果不知道 QQ 号，先创建一个通用模板并提示
    local CONFIG_DIR="$NAPCAT_DIR/config"
    mkdir -p "$CONFIG_DIR"

    # 通用 WebUI + OneBot 配置（NapCat 新版使用 napcat.json 作为主配置）
    cat > "$CONFIG_DIR/napcat.json" <<'JSON'
{
  "network": {
    "websocket": {
      "enable": true,
      "port": 6099
    },
    "http": {
      "enable": false
    }
  },
  "fileLog": {
    "enable": true,
    "level": "info"
  },
  "consoleLog": {
    "enable": true,
    "level": "info"
  }
}
JSON

    # OneBot v11 正向 WebSocket 配置（端口 3001，与 App 端 OneBotClient 默认一致）
    cat > "$CONFIG_DIR/onebot11.json" <<'JSON'
{
  "network": {
    "websocket": {
      "enable": true,
      "port": 3001
    },
    "http": {
      "enable": false,
      "port": 3000
    },
    "httpPost": {
      "enable": false
    },
    "websocketReverse": {
      "enable": false
    }
  },
  "token": "",
  "debug": false,
  "heartInterval": 30000,
  "postMessageFormat": "string"
}
JSON
    info "已写入 OneBot v11 正向 WS 配置: port=$ONEBOT_PORT"

    # 3.4 准备 LD_PRELOAD（解决 gnutls_free 符号错误）
    local GNUTLS_SO
    GNUTLS_SO=$(find /usr/lib -name "libgnutls.so.30" 2>/dev/null | head -1)
    if [ -z "$GNUTLS_SO" ]; then
        error "未找到 libgnutls.so.30，无法设置 LD_PRELOAD"
        error "请先确保第 2 步 libgnutls30 安装成功"
        return 1
    fi
    info "使用 LD_PRELOAD=$GNUTLS_SO"

    # 3.5 启动 NapCat
    info "启动 NapCat（webui 模式）..."
    warn "首次启动会弹出扫码登录二维码，请用手机 QQ 扫描"
    warn "日志会直接输出到终端，Ctrl+C 停止"
    echo
    info "启动命令：LD_PRELOAD=$GNUTLS_SO node $NAPCAT_DIR/napcat.mjs webui"
    echo

    cd "$NAPCAT_DIR"
    LD_PRELOAD="$GNUTLS_SO" node napcat.mjs webui
}

# ============================================================
#  主流程
# ============================================================
main() {
    echo "============================================================"
    echo "  NapCatQQ proot Ubuntu 一键修复脚本"
    echo "  架构: $ARCH | NapCat: $NAPCAT_DIR"
    echo "============================================================"

    # 检测是否在 proot Ubuntu 内
    if [ ! -f /etc/os-release ] || ! grep -qi ubuntu /etc/os-release; then
        warn "当前环境似乎不是 Ubuntu，请确认已执行 proot-distro login ubuntu"
    fi

    step1_fix_dpkg_and_install_deps
    step2_install_missing_libs
    step3_config_and_start_napcat
}

main "$@"
