#!/usr/bin/env bash
# ============================================================
#  NapCatQQ 容器首次初始化脚本
#  在 proot Ubuntu 容器内执行：
#    1. apt-get install 所有运行时依赖
#    2. 下载/解压 QQ NT
#    3. 解压 NapCat Shell
#    4. 写入配置
#    5. 标记完成
# ============================================================
set -e

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'
info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERR ]${NC} $*"; }

NAPCAT_DIR="/opt/napcat"
QQ_DIR="/opt/QQ"

# ============================================================
#  第 1 步：apt 安装所有依赖
# ============================================================
info "===== 第 1 步：安装系统依赖 ====="

# 修复 dpkg 状态
dpkg --configure -a 2>/dev/null || true

info "apt-get update..."
apt-get update || { error "apt update 失败，请检查网络"; exit 1; }

info "安装运行时依赖（这一步可能需要几分钟）..."
apt-get install -y --no-install-recommends \
    curl wget ca-certificates unzip \
    xvfb screen xauth procps \
    libnss3 libgbm1 libglib2.0-0 libatk1.0-0 libatk-bridge2.0-0 \
    libgtk-3-0 libasound2 libx11-xcb1 libgnutls30 \
    libxss1 libxtst6 libxrandr2 libxcomposite1 libxdamage1 \
    libxrender1 libxi6 libxext6 libxfixes3 libxkbcommon0 \
    libpango-1.0-0 libcairo2 libcups2 libdrm2 libexpat1 \
    libxcb-dri3-0 libxcb1 libdbus-1-3 \
    2>&1 || {
        warn "部分包安装失败，尝试 t64 变体..."
        apt-get install -y --no-install-recommends \
            libasound2t64 libatk1.0-0t64 libatk-bridge2.0-0t64 \
            libgtk-3-0t64 libglib2.0-0t64 2>/dev/null || true
    }

ldconfig 2>/dev/null || true
info "第 1 步完成"

# ============================================================
#  第 2 步：安装 QQ NT
# ============================================================
info "===== 第 2 步：安装 QQ NT ====="

# 优先使用用户上传的 QQ.deb
if [ -f /opt/qq-user.deb ]; then
    info "使用用户上传的 QQ.deb"
    cp /opt/qq-user.deb /tmp/QQ.deb
else
    info "从腾讯 CDN 下载 QQ NT arm64..."
    QQ_URL="https://qqdl.gtimg.cn/qqfile/QQNT/9.9.32/beta/727ce4e5/linuxqq_3.2.30-50828_arm64.deb"
    curl -L -o /tmp/QQ.deb "$QQ_URL" || {
        error "QQ 下载失败，请检查网络或手动导入 QQ.deb"
        exit 1
    }
fi

info "解压 QQ.deb 到 / ..."
dpkg -x /tmp/QQ.deb / || { error "QQ 解压失败"; exit 1; }
rm -f /tmp/QQ.deb

# 验证 QQ 安装
if [ -f "$QQ_DIR/resources/app/package.json" ]; then
    info "QQ 安装成功: $QQ_DIR/resources/app/package.json"
else
    warn "QQ 结构可能与预期不同，查找 package.json..."
    find /opt -name "package.json" -path "*/resources/app/*" 2>/dev/null | head -3
fi

info "第 2 步完成"

# ============================================================
#  第 3 步：安装 NapCat Shell
# ============================================================
info "===== 第 3 步：安装 NapCat Shell ====="

mkdir -p "$NAPCAT_DIR"

# 如果容器内有预置的 NapCat Shell zip，直接解压
if [ -f /opt/napcat/NapCat.Shell.zip ]; then
    info "解压内置 NapCat Shell..."
    unzip -o /opt/napcat/NapCat.Shell.zip -d "$NAPCAT_DIR/" || {
        error "NapCat 解压失败"
        exit 1
    }
else
    info "从 GitHub 下载 NapCat Shell..."
    NAPCAT_URL="https://github.com/NapNeko/NapCatQQ/releases/latest/download/NapCat.Shell.zip"
    curl -L -o /tmp/NapCat.Shell.zip "$NAPCAT_URL" || {
        error "NapCat 下载失败，请检查网络"
        exit 1
    }
    unzip -o /tmp/NapCat.Shell.zip -d "$NAPCAT_DIR/" || { error "NapCat 解压失败"; exit 1; }
    rm -f /tmp/NapCat.Shell.zip
fi

# 验证 NapCat
if [ -f "$NAPCAT_DIR/napcat.mjs" ]; then
    info "NapCat 安装成功: napcat.mjs"
else
    error "未找到 napcat.mjs，NapCat 安装可能失败"
    ls -la "$NAPCAT_DIR/" | head -10
    exit 1
fi

info "第 3 步完成"

# ============================================================
#  第 4 步：配置
# ============================================================
info "===== 第 4 步：写入配置 ====="

mkdir -p "$NAPCAT_DIR/config"

cat > "$NAPCAT_DIR/config/napcat.json" <<'JSON'
{
  "network": {
    "websocket": { "enable": true, "port": 6099 },
    "http": { "enable": false }
  },
  "fileLog": { "enable": true, "level": "info" },
  "consoleLog": { "enable": true, "level": "info" }
}
JSON

cat > "$NAPCAT_DIR/config/onebot11.json" <<'JSON'
{
  "network": {
    "websocket": { "enable": true, "port": 3001 },
    "http": { "enable": false, "port": 3000 },
    "httpPost": { "enable": false },
    "websocketReverse": { "enable": false }
  },
  "token": "",
  "debug": false,
  "heartInterval": 30000,
  "postMessageFormat": "string"
}
JSON

info "配置写入完成: OneBot WS port=3001"

# ============================================================
#  完成
# ============================================================
touch "$NAPCAT_DIR/.setup-done"
info "===== 初始化完成 ====="
info "NapCat 将在下次启动时自动运行"
