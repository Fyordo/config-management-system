"""Binary stream decoder for the CMS agent socket protocol.

Message layout (AGENT_CONTRACT.MD):
  [key_len: uint32 BE] [key: key_len bytes]
  [value_len: uint32 BE] [value: value_len bytes]
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import BinaryIO, Optional


_UINT32_FMT = struct.Struct(">I")  # big-endian unsigned 32-bit


@dataclass(frozen=True)
class PropertyUpdateMessage:
    """Immutable container for a single property update received from the agent."""

    key: str
    value: bytes

    def __post_init__(self) -> None:
        # Ensure value is always an immutable copy stored as bytes.
        object.__setattr__(self, "value", bytes(self.value))


class PropertyUpdateStreamReader:
    """Reads length-prefixed property-update messages from a binary stream.

    The stream is *not* closed by this class; lifecycle management is left to
    the caller.
    """

    def __init__(self, stream: BinaryIO) -> None:
        self._stream = stream

    def read_message(self) -> Optional[PropertyUpdateMessage]:
        """Read and return the next message from the stream.

        Returns:
            A :class:`PropertyUpdateMessage` when a complete message was read.
            ``None`` on a clean EOF at a message boundary.

        Raises:
            EOFError: When the stream ends in the middle of a message.
            OSError: On any underlying I/O error.
        """
        key_len = self._read_uint32(allow_eof=True)
        if key_len is None:
            return None  # clean EOF between messages

        key_bytes = self._read_exactly(key_len, context="key")
        value_len = self._read_uint32(allow_eof=False)
        value_bytes = self._read_exactly(value_len, context="value")  # type: ignore[arg-type]

        return PropertyUpdateMessage(key=key_bytes.decode("utf-8"), value=value_bytes)

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------

    def _read_uint32(self, *, allow_eof: bool) -> Optional[int]:
        raw = self._stream.read(_UINT32_FMT.size)
        if not raw:
            if allow_eof:
                return None
            raise EOFError("Unexpected EOF while reading length field")
        if len(raw) < _UINT32_FMT.size:
            raise EOFError(
                f"Unexpected EOF: expected {_UINT32_FMT.size} bytes for length field, "
                f"got {len(raw)}"
            )
        (value,) = _UINT32_FMT.unpack(raw)
        return value

    def _read_exactly(self, n: int, *, context: str) -> bytes:
        buf = bytearray()
        while len(buf) < n:
            chunk = self._stream.read(n - len(buf))
            if not chunk:
                raise EOFError(
                    f"Unexpected EOF while reading {context} "
                    f"({len(buf)}/{n} bytes received)"
                )
            buf.extend(chunk)
        return bytes(buf)
