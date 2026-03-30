"""Tests for PropertyUpdateStreamReader and PropertyUpdateMessage."""

import io
import struct

import pytest

from cms import PropertyUpdateMessage, PropertyUpdateStreamReader


def _encode_message(key: str, value: bytes) -> bytes:
    # Protobuf payload:
    #   1: string key
    #   2: bytes  value
    #
    # Keep the encoder minimal and independent of generated proto code.
    key_b = key.encode("utf-8")

    def _varint(n: int) -> bytes:
        out = bytearray()
        while True:
            to_write = n & 0x7F
            n >>= 7
            if n:
                out.append(to_write | 0x80)
            else:
                out.append(to_write)
                break
        return bytes(out)

    # field 1 tag = (1<<3)|2 = 0x0A
    # field 2 tag = (2<<3)|2 = 0x12
    payload = b"".join(
        [
            b"\x0A",
            _varint(len(key_b)),
            key_b,
            b"\x12",
            _varint(len(value)),
            value,
        ]
    )
    return struct.pack(">I", len(payload)) + payload


def _make_reader(data: bytes) -> PropertyUpdateStreamReader:
    return PropertyUpdateStreamReader(io.BytesIO(data))


# ---------------------------------------------------------------------------
# Happy-path
# ---------------------------------------------------------------------------


def test_single_message_then_eof():
    data = _encode_message("my.key", b"hello")
    reader = _make_reader(data)

    msg = reader.read_message()
    assert msg is not None
    assert msg.key == "my.key"
    assert msg.value == b"hello"

    assert reader.read_message() is None


def test_two_messages_sequential():
    data = _encode_message("k1", b"v1") + _encode_message("k2", b"\x00\x01\x02")
    reader = _make_reader(data)

    m1 = reader.read_message()
    m2 = reader.read_message()
    eof = reader.read_message()

    assert m1 is not None and m1.key == "k1" and m1.value == b"v1"
    assert m2 is not None and m2.key == "k2" and m2.value == b"\x00\x01\x02"
    assert eof is None


def test_empty_value():
    data = _encode_message("flag", b"")
    reader = _make_reader(data)
    msg = reader.read_message()
    assert msg is not None
    assert msg.value == b""


def test_binary_value_opaque():
    value = bytes(range(256))
    data = _encode_message("bin", value)
    msg = _make_reader(data).read_message()
    assert msg is not None
    assert msg.value == value


def test_clean_eof_on_empty_stream():
    reader = _make_reader(b"")
    assert reader.read_message() is None


# ---------------------------------------------------------------------------
# Error cases
# ---------------------------------------------------------------------------


def test_truncated_key_bytes_raises():
    # Truncate in the middle of the payload
    data = _encode_message("some.key", b"v")[:5]
    with pytest.raises(EOFError):
        _make_reader(data).read_message()


def test_eof_between_key_and_value_len_raises():
    # Payload length is present, but payload bytes are missing.
    data = struct.pack(">I", 10)
    with pytest.raises(EOFError):
        _make_reader(data).read_message()


def test_truncated_value_bytes_raises():
    data = _encode_message("k", b"long value")[:-2]  # truncated payload
    with pytest.raises(EOFError):
        _make_reader(data).read_message()


# ---------------------------------------------------------------------------
# PropertyUpdateMessage immutability
# ---------------------------------------------------------------------------


def test_message_value_is_immutable_copy():
    original = bytearray(b"data")
    msg = PropertyUpdateMessage(key="k", value=original)
    original[0] = 0xFF  # mutate original
    assert msg.value[0] == ord("d"), "value must not be affected by mutation of source"


def test_message_equality():
    m1 = PropertyUpdateMessage(key="k", value=b"v")
    m2 = PropertyUpdateMessage(key="k", value=b"v")
    assert m1 == m2


def test_message_inequality_on_key():
    assert PropertyUpdateMessage("k1", b"v") != PropertyUpdateMessage("k2", b"v")


def test_message_inequality_on_value():
    assert PropertyUpdateMessage("k", b"a") != PropertyUpdateMessage("k", b"b")
