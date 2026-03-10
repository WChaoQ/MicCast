# ══════════════════════════════════════════════════════════════════════════════
#  core/network_utils.py
#  网络辅助工具
# ══════════════════════════════════════════════════════════════════════════════

import socket


def get_local_ip() -> str:
    """获取本机在局域网中的 IP 地址。"""
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"


def is_port_available(port: int) -> bool:
    """检查端口是否可用（未被占用）。"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        try:
            s.bind(("0.0.0.0", port))
            return True
        except OSError:
            return False
