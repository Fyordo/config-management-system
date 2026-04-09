from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import BinaryIO, Optional


_UINT32_FMT = struct.Struct(">I")  # big-endian unsigned 32-bit
_MAX_PAYLOAD_LENGTH = 1024 * 1024  # 1 MB


@dataclass(frozen=True)
class PropertyUpdateMessage:
    key: str
    value: bytes

    def __post_init__(self) -> None:
        # Ensure value is always an immutable copy stored as bytes.
        object.__setattr__(self, "value", bytes(self.value))


class PropertyUpdateStreamReader:
    def __init__(self, stream: BinaryIO) -> None:
        self._stream = stream

    def read_message(self) -> Optional[PropertyUpdateMessage]:
        payload_len = self._read_uint32(allow_eof=True)
        if payload_len is None:
            return None  # clean EOF between messages

        if payload_len > _MAX_PAYLOAD_LENGTH:
            raise ValueError(
                f"Payload length {payload_len} exceeds maximum {_MAX_PAYLOAD_LENGTH}"
            )

        payload = self._read_exactly(payload_len, context="payload")  # type: ignore[arg-type]
        key, value = _parse_property_payload(payload)

        return PropertyUpdateMessage(key=key, value=value)

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

def _parse_property_payload(payload: bytes) -> tuple[str, bytes]:
    i = 0
    key = ""
    value = b""

    while i < len(payload):
        tag, i = _read_varint(payload, i)
        field_no = tag >> 3
        wire_type = tag & 0x07

        if wire_type == 2:  # length-delimited
            length, i = _read_varint(payload, i)
            if i + length > len(payload):
                raise ValueError("Invalid protobuf payload: truncated length-delimited field")
            field_bytes = payload[i : i + length]
            i += length

            if field_no == 1:
                key = field_bytes.decode("utf-8")
            elif field_no == 2:
                value = bytes(field_bytes)
            continue

        if wire_type == 0:  # varint
            _, i = _read_varint(payload, i)
        elif wire_type == 1:  # 64-bit
            if i + 8 > len(payload):
                raise ValueError("Invalid protobuf payload: truncated 64-bit field")
            i += 8
        elif wire_type == 5:  # 32-bit
            if i + 4 > len(payload):
                raise ValueError("Invalid protobuf payload: truncated 32-bit field")
            i += 4
        else:
            raise ValueError(f"Invalid protobuf payload: unsupported wire type {wire_type}")

    return key, value


def _read_varint(data: bytes, offset: int) -> tuple[int, int]:
    shift = 0
    result = 0
    i = offset
    while i < len(data):
        b = data[i]
        i += 1
        result |= (b & 0x7F) << shift
        if (b & 0x80) == 0:
            return result, i
        shift += 7
        if shift >= 64:
            raise ValueError("Invalid protobuf payload: varint is too long")
    raise ValueError("Invalid protobuf payload: truncated varint")
