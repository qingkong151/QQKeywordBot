#!/bin/bash
set -e

INSTALL_DIR="/data/adb/napcat"
ROOTFS="$INSTALL_DIR/rootfs"
QQ_DIR="$INSTALL_DIR/QQ"
NAPCAT_DIR="$INSTALL_DIR/napcat"
LOG="$INSTALL_DIR/napcat.log"
QQ_URL="https://qqdl.gtimg.cn/qqfile/QQNT/9.9.32/beta/727ce4e5/linuxqq_3.2.30-50828_arm64.deb"
NAPCAT_URL="https://github.com/NapNeko/NapCatQQ/releases/latest/download/NapCat.Shell.zip"

log() { echo "[$(date +%H:%M:%S)] [napcat] $*" | tee -a "$LOG"; }

mount_chroot() {
    case "$1" in
        mount)
            mount --bind /dev "$ROOTFS/dev" 2>/dev/null || true
            mount --bind /proc "$ROOTFS/proc" 2>/dev/null || true
            mount --bind /sys "$ROOTFS/sys" 2>/dev/null || true
            ;;
        umount)
            mount | grep "$ROOTFS" | awk '{print $3}' | sort -r | while read m; do
                umount "$m" 2>/dev/null || true
            done
            ;;
    esac
}

case "${1:-install}" in
install)
    if [ ! -d "$ROOTFS/usr/bin" ]; then
        log "下载 ubuntu-base-arm64..."
        mkdir -p "$INSTALL_DIR"
        curl -L -o /tmp/ubuntu-base.tar.gz \
            "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ubuntu-base-24.04-base-arm64.tar.gz"
        log "解压 rootfs..."
        rm -rf "$ROOTFS" && mkdir -p "$ROOTFS"
        tar xf /tmp/ubuntu-base.tar.gz -C "$ROOTFS"
        rm -f /tmp/ubuntu-base.tar.gz

        cat > "$ROOTFS/etc/apt/sources.list.d/ubuntu.sources" << 'SRCEOF'
Types: deb
URIs: http://ports.ubuntu.com/ubuntu-ports/
Suites: noble noble-updates noble-security
Components: main universe restricted multiverse
Signed-By: /usr/share/keyrings/ubuntu-archive-keyring.gpg
SRCEOF
        rm -f "$ROOTFS/etc/apt/sources.list"
    else
        log "rootfs 已存在"
    fi

    log "chroot 内安装依赖（首次最慢）..."
    mount_chroot mount
    chroot "$ROOTFS" /bin/bash -c '
        export DEBIAN_FRONTEND=noninteractive
        apt-get update &&
        apt-get install -y --no-install-recommends \
            curl wget ca-certificates unzip file \
            xvfb xauth procps libdbus-1-3 \
            libnss3 libgbm1 libglib2.0-0 libatk1.0-0 libatk-bridge2.0-0 \
            libgtk-3-0 libasound2 libx11-xcb1 libgnutls30 \
            libxss1 libxtst6 libxrandr2 libxcomposite1 libxdamage1 \
            libxrender1 libxi6 libxext6 libxfixes3 libxkbcommon0 \
            libpango-1.0-0 libcairo2 libcups2 libdrm2 libexpat1 \
            libxcb-dri3-0 libxcb1
    ' 2>&1 | tee -a "$LOG" | tail -20

    log "安装 QQ NT..."
    if [ -f "$INSTALL_DIR/QQ.deb" ]; then
        log "使用已有的 QQ.deb"
        cp "$INSTALL_DIR/QQ.deb" /tmp/QQ.deb
    else
        log "下载 QQ..."
        curl -L -o /tmp/QQ.deb "$QQ_URL"
    fi
    dpkg-deb -x /tmp/QQ.deb "$ROOTFS"
    rm -f /tmp/QQ.deb
    log "QQ OK"

    log "安装 NapCat Shell..."
    curl -L -o /tmp/NapCat.Shell.zip "$NAPCAT_URL"
    mkdir -p "$NAPCAT_DIR"
    unzip -o /tmp/NapCat.Shell.zip -d "$NAPCAT_DIR/"
    rm -f /tmp/NapCat.Shell.zip
    log "NapCat OK"

    mkdir -p "$NAPCAT_DIR/config"
    cat > "$NAPCAT_DIR/config/napcat.json" << 'JSON'
{
  "network": { "websocket": { "enable": true, "port": 6099 }, "http": { "enable": false } },
  "fileLog": { "enable": true, "level": "info" },
  "consoleLog": { "enable": true, "level": "info" }
}
JSON
    cat > "$NAPCAT_DIR/config/onebot11.json" << 'JSON'
{
  "network": {
    "websocket": { "enable": true, "port": 3001 },
    "http": { "enable": false, "port": 3000 },
    "httpPost": { "enable": false },
    "websocketReverse": { "enable": false }
  },
  "token": "", "debug": false, "heartInterval": 30000, "postMessageFormat": "string"
}
JSON

    mount_chroot umount
    touch "$INSTALL_DIR/.setup-done"
    log "安装完成 ✅"
    ;;

start)
    if [ ! -f "$INSTALL_DIR/.setup-done" ]; then
        log "未安装，请先 run install"
        exit 1
    fi
    mount_chroot mount
    mkdir -p "$ROOTFS/opt/QQ" "$ROOTFS/opt/napcat"
    mount --bind "$QQ_DIR" "$ROOTFS/opt/QQ" 2>/dev/null || true
    mount --bind "$NAPCAT_DIR" "$ROOTFS/opt/napcat" 2>/dev/null || true
    ln -sfn /opt/QQ/resources/app "$ROOTFS/usr/local/bin/resources/app"
    > "$LOG"
    chroot "$ROOTFS" /bin/bash -c "
        cd /opt/napcat
        GNUTLS=\$(find /usr/lib -name libgnutls.so.30 2>/dev/null | head -1)
        [ -n \"\$GNUTLS\" ] && export LD_PRELOAD=\$GNUTLS
        export DISPLAY=:99
        node napcat.mjs webui >> "$LOG" 2>&1 &
        echo \$!
    "
    log "NapCat 已后台启动"
    ;;

stop)
    log "停止 NapCat..."
    pkill -f "napcat.mjs" 2>/dev/null || true
    mount_chroot umount
    log "已停止"
    ;;

status)
    [ -f "$INSTALL_DIR/.setup-done" ] && echo "installed: yes" || echo "installed: no"
    mount_chroot mount
    pgrep -f "napcat.mjs" >/dev/null && echo "running: yes" || echo "running: no"
    mount_chroot umount
    ;;

*)
    echo "用法: $0 {install|start|stop|status}"
    exit 1
    ;;
esac
