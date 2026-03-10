# ══════════════════════════════════════════════════════════════════════════════
#  core/jitter.py
#  基于最小堆的抖动缓冲（Jitter Buffer）
# ══════════════════════════════════════════════════════════════════════════════

import heapq
from .constants import SEQ_WINDOW, JITTER_WIFI_DEFAULT


class JitterBuffer:
    """
    积累 target_size 帧后按序号顺序输出，自动丢弃严重乱序的包。
    断开重连时调用 reset() 清空所有状态重新预热。
    """

    def __init__(self, target_size: int = JITTER_WIFI_DEFAULT):
        self._target   = target_size
        self._heap:    list = []
        self._started  = False
        self._next_seq = 0
        self._dropped  = 0
        self._total    = 0

    # ── 公开接口 ──────────────────────────────────────────────────────────────

    def reset(self):
        self._heap.clear()
        self._started  = False
        self._next_seq = 0
        self._dropped  = 0
        self._total    = 0

    def push(self, seq: int, data: bytes):
        self._total += 1
        # 丢弃已过期的包（比期望序号落后超过 SEQ_WINDOW）
        if self._started and (self._next_seq - seq) % (2 ** 32) < SEQ_WINDOW:
            self._dropped += 1
            return
        heapq.heappush(self._heap, (seq, data))

    def pop(self) -> bytes | None:
        """返回下一帧数据，缓冲未满或队列为空时返回 None。"""
        if not self._started:
            if len(self._heap) >= self._target:
                self._started = True
            else:
                return None
        if not self._heap:
            return None
        seq, data = heapq.heappop(self._heap)
        self._next_seq = (seq + 1) % (2 ** 32)
        return data

    # ── 统计 ──────────────────────────────────────────────────────────────────

    @property
    def total(self) -> int:
        return self._total

    @property
    def dropped(self) -> int:
        return self._dropped

    @property
    def buffered(self) -> int:
        return len(self._heap)

    def stats_str(self) -> str:
        rate = (self._dropped / self._total * 100) if self._total else 0.0
        return (
            f"收包={self._total}  丢包={self._dropped}"
            f"  丢包率={rate:.1f}%  缓冲={len(self._heap)}"
        )
