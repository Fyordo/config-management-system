"""Tests for PropertyManager."""

import json
import os
import socket
import struct
import tempfile
import threading
import time

import pytest

from cms import InMemoryPropertyRepository, PropertyManager


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

RESOURCES_DIR = os.path.join(os.path.dirname(__file__), "resources")


def _config_path(name: str) -> str:
    return os.path.join(RESOURCES_DIR, name)


def _write_temp_json(data: dict, tmp_path) -> str:
    p = tmp_path / "config.json"
    p.write_text(json.dumps(data))
    return str(p)


def _encode_message(key: str, value: bytes) -> bytes:
    key_bytes = key.encode()
    return (
        struct.pack(">I", len(key_bytes))
        + key_bytes
        + struct.pack(">I", len(value))
        + value
    )


# ---------------------------------------------------------------------------
# Config file loading
# ---------------------------------------------------------------------------


def test_blank_file_raises(tmp_path):
    blank = tmp_path / "empty.json"
    blank.write_text("")
    pm = PropertyManager(config_file_path=str(blank))
    with pytest.raises(ValueError, match="blank"):
        pm.init()


def test_invalid_json_raises(tmp_path):
    bad = tmp_path / "bad.json"
    bad.write_text("{not valid json")
    pm = PropertyManager(config_file_path=str(bad))
    with pytest.raises(ValueError, match="JSON"):
        pm.init()


def test_non_object_json_raises(tmp_path):
    arr = tmp_path / "arr.json"
    arr.write_text("[1, 2, 3]")
    pm = PropertyManager(config_file_path=str(arr))
    with pytest.raises(ValueError, match="object"):
        pm.init()


def test_missing_file_raises(tmp_path):
    pm = PropertyManager(config_file_path=str(tmp_path / "nonexistent.json"))
    with pytest.raises(OSError):
        pm.init()


def test_loads_all_types_from_file():
    pm = PropertyManager(config_file_path=_config_path("application.json"))
    pm.init()

    assert pm.get("app.int.val") == 123
    assert pm.get("app.long.val") == 123123123123
    assert pm.get("app.string.val") == "SomeRandomString"
    assert pm.get("app.list.val") == [1, 2, 3, 4]
    assert pm.get("app.object.val") == {
        "field1": 123,
        "field2": 123.23,
        "field3": "AnotherRandomString",
    }
    assert pm.get("app.bool.val") is True


# ---------------------------------------------------------------------------
# Callback dispatch
# ---------------------------------------------------------------------------


def test_default_callback_receives_old_and_new(tmp_path):
    events: list = []
    pm = PropertyManager(
        config_file_path=_write_temp_json({"k": "initial"}, tmp_path),
        default_callback=lambda key, old, new: events.append((key, old, new)),
    )
    pm.init()

    # The load from file fires the default callback because no per-key cb is set.
    assert ("k", None, "initial") in events


def test_per_key_callback_overrides_default(tmp_path):
    default_events: list = []
    key_events: list = []

    pm = PropertyManager(
        config_file_path=_write_temp_json({"k1": 1, "k2": 2}, tmp_path),
        default_callback=lambda k, o, n: default_events.append(k),
    )
    pm.add_update_callback("k1", lambda k, o, n: key_events.append((k, o, n)))
    pm.init()

    assert "k1" not in default_events, "per-key cb should shadow default for k1"
    assert "k2" in default_events
    assert any(e[0] == "k1" for e in key_events)


def test_remove_callback(tmp_path):
    events: list = []
    pm = PropertyManager(
        config_file_path=_write_temp_json({}, tmp_path),
    )
    pm.add_update_callback("k", lambda k, o, n: events.append(n))
    pm.remove_update_callback("k")
    pm.init()
    pm._store("k", "value")  # noqa: SLF001 — direct call for test isolation
    assert events == [], "callback should not fire after removal"


# ---------------------------------------------------------------------------
# UNIX socket integration
# ---------------------------------------------------------------------------


def _send_updates(sock_path: str, messages: list[tuple[str, bytes]], delay: float = 0.05) -> None:
    """Connect to the SDK's server socket and send messages, then disconnect."""
    time.sleep(delay)
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as s:
        s.connect(sock_path)
        for key, value in messages:
            s.sendall(_encode_message(key, value))


def test_socket_updates_stored(tmp_path):
    # AF_UNIX path is limited to 104 bytes on macOS; use a short path in /tmp.
    sock_path = tempfile.mktemp(suffix=".sock", dir="/tmp")
    pm = PropertyManager(
        config_file_path=_write_temp_json({"a": "init"}, tmp_path),
        unix_socket_path=sock_path,
    )
    pm.init()

    t = threading.Thread(
        target=_send_updates,
        args=(sock_path, [("a", b"updated"), ("b", b"\x01\x02")]),
    )
    t.start()
    t.join(timeout=3)

    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        if pm.get("a") == b"updated" and pm.get("b") == b"\x01\x02":
            break
        time.sleep(0.05)

    assert pm.get("a") == b"updated"
    assert pm.get("b") == b"\x01\x02"


def test_socket_callback_fires_on_update(tmp_path):
    sock_path = tempfile.mktemp(suffix=".sock", dir="/tmp")
    received: list = []

    pm = PropertyManager(
        config_file_path=_write_temp_json({}, tmp_path),
        unix_socket_path=sock_path,
    )
    pm.add_update_callback("x", lambda k, o, n: received.append((k, o, n)))
    pm.init()

    threading.Thread(
        target=_send_updates,
        args=(sock_path, [("x", b"val")]),
    ).start()

    deadline = time.monotonic() + 2
    while time.monotonic() < deadline:
        if received:
            break
        time.sleep(0.05)

    assert received == [("x", None, b"val")]


def test_no_socket_path_does_not_raise(tmp_path):
    """PropertyManager.init() must succeed even with no socket configured."""
    pm = PropertyManager(
        config_file_path=_write_temp_json({"k": 1}, tmp_path),
        unix_socket_path="",
    )
    # Must not raise even though no socket path is provided.
    pm.init()
    assert pm.get("k") == 1
