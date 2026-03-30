"""Binary stream decoder for the CMS agent socket protocol.

Message layout (agent/AGENT_CONTRACT.md):
  [payload_len: uint32 BE] [payload: payload_len bytes]

Where payload is a protobuf-serialized com.fyordo.cms.Property:
  string key = 1;
  bytes  value = 2;
"""

from __future__ import annotations

import struct
from dataclasses import dataclass
from typing import BinaryIO, Optional

from google.protobuf import descriptor_pb2, descriptor_pool, message_factory


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
        payload_len = self._read_uint32(allow_eof=True)
        if payload_len is None:
            return None  # clean EOF between messages

        payload = self._read_exactly(payload_len, context="payload")  # type: ignore[arg-type]
        key, value = _parse_property_payload(payload)

        return PropertyUpdateMessage(key=key, value=value)

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


def _property_message_cls():
    """Return a dynamic protobuf message class for com.fyordo.cms.Property."""
    file_proto = descriptor_pb2.FileDescriptorProto()
    file_proto.name = "cms_property.proto"
    file_proto.package = "com.fyordo.cms"
    file_proto.syntax = "proto3"

    msg = file_proto.message_type.add()
    msg.name = "Property"

    f1 = msg.field.add()
    f1.name = "key"
    f1.number = 1
    f1.label = descriptor_pb2.FieldDescriptorProto.LABEL_OPTIONAL
    f1.type = descriptor_pb2.FieldDescriptorProto.TYPE_STRING

    f2 = msg.field.add()
    f2.name = "value"
    f2.number = 2
    f2.label = descriptor_pb2.FieldDescriptorProto.LABEL_OPTIONAL
    f2.type = descriptor_pb2.FieldDescriptorProto.TYPE_BYTES

    pool = descriptor_pool.DescriptorPool()
    pool.Add(file_proto)
    desc = pool.FindMessageTypeByName("com.fyordo.cms.Property")
    return message_factory.GetMessageClass(desc)


_PropertyMessage = _property_message_cls()


def _parse_property_payload(payload: bytes) -> tuple[str, bytes]:
    try:
        msg = _PropertyMessage.FromString(payload)
    except Exception as exc:  # noqa: BLE001
        raise ValueError("Failed to parse protobuf Property payload") from exc
    return str(msg.key), bytes(msg.value)
