#!/usr/bin/env bash
set -euo pipefail

# =============================================================================
# dev.sh — CrabShell 開発用プロセス管理スクリプト
#
# Usage:
#   ./dev.sh server start|stop|restart|log|status
#   ./dev.sh frontend start|stop|restart|log|status
#   ./dev.sh start|stop|restart|status
# =============================================================================

DEV_DIR=".dev"
SERVER_PID_FILE="$DEV_DIR/server.pid"
SERVER_LOG_FILE="$DEV_DIR/server.log"
FRONTEND_PID_FILE="$DEV_DIR/frontend.pid"
FRONTEND_LOG_FILE="$DEV_DIR/frontend.log"

SERVER_PORT=8080
FRONTEND_PORT=3000

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

info()  { echo -e "${CYAN}[info]${NC}  $*"; }
ok()    { echo -e "${GREEN}[ok]${NC}    $*"; }
warn()  { echo -e "${YELLOW}[warn]${NC}  $*"; }
err()   { echo -e "${RED}[error]${NC} $*" >&2; }

ensure_dev_dir() {
    mkdir -p "$DEV_DIR"
}

# -----------------------------------------------------------------------------
# PID / process helpers
# -----------------------------------------------------------------------------

# Check if a process (by PID) is alive
is_pid_alive() {
    local pid="$1"
    kill -0 "$pid" 2>/dev/null
}

# Read PID from file, return 0 if valid and alive
read_pid() {
    local pid_file="$1"
    if [[ -f "$pid_file" ]]; then
        local pid
        pid=$(<"$pid_file")
        if [[ -n "$pid" ]] && is_pid_alive "$pid"; then
            echo "$pid"
            return 0
        fi
        # Stale PID file — clean up
        rm -f "$pid_file"
    fi
    return 1
}

# Kill process group by PID, fall back to port-based kill
stop_process() {
    local name="$1"
    local pid_file="$2"
    local port="$3"

    local pid
    if pid=$(read_pid "$pid_file"); then
        info "$name を停止中... (PID: $pid)"
        # Kill the process group (negative PID)
        kill -- -"$pid" 2>/dev/null || kill "$pid" 2>/dev/null || true
        # Wait for process to exit (max 10 seconds)
        local waited=0
        while is_pid_alive "$pid" && (( waited < 10 )); do
            sleep 1
            (( ++waited ))
        done
        if is_pid_alive "$pid"; then
            warn "SIGTERM で停止しなかったため SIGKILL を送信"
            kill -9 -- -"$pid" 2>/dev/null || kill -9 "$pid" 2>/dev/null || true
        fi
        rm -f "$pid_file"
        ok "$name を停止しました"
        return 0
    fi

    # Fallback: check port
    if command -v lsof &>/dev/null; then
        local port_pids
        port_pids=$(lsof -ti :"$port" 2>/dev/null || true)
        if [[ -n "$port_pids" ]]; then
            warn "PID ファイルなし。ポート $port のプロセスを停止します"
            echo "$port_pids" | xargs kill 2>/dev/null || true
            rm -f "$pid_file"
            ok "$name を停止しました (port fallback)"
            return 0
        fi
    fi

    info "$name は起動していません"
    rm -f "$pid_file"
    return 0
}

# Wait until a port is listening (max timeout seconds)
wait_for_port() {
    local port="$1"
    local timeout="${2:-120}"
    local waited=0
    while ! lsof -ti :"$port" &>/dev/null && (( waited < timeout )); do
        sleep 2
        (( waited += 2 ))
        printf "."
    done
    echo ""
    lsof -ti :"$port" &>/dev/null
}

# Print status for a service
show_status() {
    local name="$1"
    local pid_file="$2"
    local port="$3"

    local pid
    if pid=$(read_pid "$pid_file"); then
        ok "$name: 起動中 (PID: $pid, port: $port)"
    else
        info "$name: 停止中"
    fi
}

# -----------------------------------------------------------------------------
# Server commands
# -----------------------------------------------------------------------------

server_start() {
    ensure_dev_dir

    # Stop existing process if any
    if read_pid "$SERVER_PID_FILE" >/dev/null 2>&1; then
        warn "サーバーが既に起動中です。再起動します..."
        stop_process "サーバー" "$SERVER_PID_FILE" "$SERVER_PORT"
    fi

    # フロントエンド起動中は Gradle ロックが競合する可能性がある
    if read_pid "$FRONTEND_PID_FILE" >/dev/null 2>&1; then
        warn "フロントエンドが起動中です。Gradle ロック待ちになる場合は先に './dev.sh frontend stop' してください"
    fi

    info "サーバーをビルド中..."
    if ! ./gradlew :server:buildFatJar -PskipFrontend; then
        err "サーバーのビルドに失敗しました"
        return 1
    fi

    info "サーバーを起動中..."
    setsid java -jar server/build/libs/server-all.jar > "$SERVER_LOG_FILE" 2>&1 &
    local pid=$!
    echo "$pid" > "$SERVER_PID_FILE"

    # Brief wait to catch immediate crashes
    sleep 2
    if ! is_pid_alive "$pid"; then
        err "サーバーの起動に失敗しました。ログを確認してください:"
        err "  ./dev.sh server log"
        rm -f "$SERVER_PID_FILE"
        return 1
    fi

    info "ポート $SERVER_PORT の待ち受け開始を待機中..."
    if wait_for_port "$SERVER_PORT" 60; then
        ok "サーバーを起動しました (PID: $pid, http://localhost:$SERVER_PORT)"
    else
        warn "サーバープロセスは起動しましたが、ポート $SERVER_PORT がまだ開いていません"
        warn "ログを確認してください: ./dev.sh server log"
    fi
}

server_stop() {
    stop_process "サーバー" "$SERVER_PID_FILE" "$SERVER_PORT"
}

server_restart() {
    server_stop
    server_start
}

server_log() {
    if [[ -f "$SERVER_LOG_FILE" ]]; then
        tail -f "$SERVER_LOG_FILE"
    else
        warn "サーバーのログファイルがありません: $SERVER_LOG_FILE"
    fi
}

server_status() {
    show_status "サーバー" "$SERVER_PID_FILE" "$SERVER_PORT"
}

# -----------------------------------------------------------------------------
# Frontend commands
# -----------------------------------------------------------------------------

frontend_start() {
    ensure_dev_dir

    # Stop existing process if any
    if read_pid "$FRONTEND_PID_FILE" >/dev/null 2>&1; then
        warn "フロントエンドが既に起動中です。再起動します..."
        stop_process "フロントエンド" "$FRONTEND_PID_FILE" "$FRONTEND_PORT"
    fi

    info "フロントエンドを起動中..."
    setsid env BROWSER_OPEN=false ./gradlew :app:wasmJsBrowserDevelopmentRun > "$FRONTEND_LOG_FILE" 2>&1 &
    local pid=$!
    echo "$pid" > "$FRONTEND_PID_FILE"

    # Brief wait to catch immediate crashes
    sleep 2
    if ! is_pid_alive "$pid"; then
        err "フロントエンドの起動に失敗しました。ログを確認してください:"
        err "  ./dev.sh frontend log"
        rm -f "$FRONTEND_PID_FILE"
        return 1
    fi

    info "ポート $FRONTEND_PORT の待ち受け開始を待機中 (ビルドに数分かかる場合があります)..."
    if wait_for_port "$FRONTEND_PORT" 300; then
        ok "フロントエンドを起動しました (PID: $pid, http://localhost:$FRONTEND_PORT)"
    else
        warn "フロントエンドプロセスは起動しましたが、ポート $FRONTEND_PORT がまだ開いていません"
        warn "ログを確認してください: ./dev.sh frontend log"
    fi
}

frontend_stop() {
    stop_process "フロントエンド" "$FRONTEND_PID_FILE" "$FRONTEND_PORT"
    # Gradle daemon が webpack を子プロセスとして起動するため、ポートで残留プロセスも停止
    if command -v lsof &>/dev/null; then
        local remaining
        remaining=$(lsof -ti :"$FRONTEND_PORT" 2>/dev/null || true)
        if [[ -n "$remaining" ]]; then
            echo "$remaining" | xargs kill 2>/dev/null || true
        fi
    fi
}

frontend_restart() {
    frontend_stop
    frontend_start
}

frontend_log() {
    if [[ -f "$FRONTEND_LOG_FILE" ]]; then
        tail -f "$FRONTEND_LOG_FILE"
    else
        warn "フロントエンドのログファイルがありません: $FRONTEND_LOG_FILE"
    fi
}

frontend_status() {
    show_status "フロントエンド" "$FRONTEND_PID_FILE" "$FRONTEND_PORT"
}

# -----------------------------------------------------------------------------
# Combined commands
# -----------------------------------------------------------------------------

all_start() {
    server_start
    frontend_start
}

all_stop() {
    frontend_stop
    server_stop
}

all_restart() {
    all_stop
    all_start
}

all_status() {
    server_status
    frontend_status
}

# -----------------------------------------------------------------------------
# Usage
# -----------------------------------------------------------------------------

usage() {
    cat <<'USAGE'
Usage: ./dev.sh <command>

サービス別コマンド:
  ./dev.sh server start       fat JAR ビルド → サーバー起動
  ./dev.sh server stop        サーバー停止
  ./dev.sh server restart     サーバー再起動
  ./dev.sh server log         サーバーログを tail -f
  ./dev.sh server status      サーバーの状態を表示

  ./dev.sh frontend start     webpack dev server 起動
  ./dev.sh frontend stop      フロントエンド停止
  ./dev.sh frontend restart   フロントエンド再起動
  ./dev.sh frontend log       フロントエンドログを tail -f
  ./dev.sh frontend status    フロントエンドの状態を表示

一括コマンド:
  ./dev.sh start              サーバー + フロントエンド起動
  ./dev.sh stop               両方停止
  ./dev.sh restart            両方再起動
  ./dev.sh status             両方の状態を表示
USAGE
}

# -----------------------------------------------------------------------------
# Main dispatch
# -----------------------------------------------------------------------------

case "${1:-}" in
    server)
        case "${2:-}" in
            start)   server_start ;;
            stop)    server_stop ;;
            restart) server_restart ;;
            log)     server_log ;;
            status)  server_status ;;
            *)       usage; exit 1 ;;
        esac
        ;;
    frontend)
        case "${2:-}" in
            start)   frontend_start ;;
            stop)    frontend_stop ;;
            restart) frontend_restart ;;
            log)     frontend_log ;;
            status)  frontend_status ;;
            *)       usage; exit 1 ;;
        esac
        ;;
    start)   all_start ;;
    stop)    all_stop ;;
    restart) all_restart ;;
    status)  all_status ;;
    help|-h|--help) usage ;;
    *)       usage; exit 1 ;;
esac
