"""High-level orchestrator: loads config from JSON and listens for socket updates."""

from __future__ import annotations

import json
import logging
import os
import socket
import threading
from typing import Any, Callable, Optional, cast

from .property_repository import InMemoryPropertyRepository, PropertyRepository
from .socket_reader import PropertyUpdateStreamReader

logger = logging.getLogger(__name__)

UpdateCallback = Callable[[str, Optional[str], Optional[str]], None]

_ENV_SOCKET_PATH = "CMS_UNIX_SOCKET_PATH"


def _noop_callback(key: str, old: Optional[str], new: Optional[str]) -> None:  # noqa: ARG001
    pass


def _json_value_to_storage_string(value: Any) -> str:
    """Match Java/Go SDK: JSON strings are kept raw; other JSON values are re-serialized."""
    if isinstance(value, str):
        return value
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"))


class PropertyManager:
    """Loads initial configuration from a JSON file and keeps it current via a
    UNIX domain socket.

    The SDK acts as the **server**: it creates and listens on the socket so that
    the CMS agent (client) can connect and stream property updates.

    Args:
        config_file_path:
            Path to the JSON file containing the initial property map
            (``{"key": <any JSON value>, ...}``). Values are normalized to
            strings (JSON strings stay as-is; other types use compact
            ``json.dumps``) before storage, matching the Java SDK.
        unix_socket_path:
            Path for the UNIX domain socket.  Defaults to the value of the
            ``CMS_UNIX_SOCKET_PATH`` environment variable when *not* supplied.
        repository:
            Storage backend.  Defaults to a new
            :class:`~cms.InMemoryPropertyRepository` when omitted.
        default_callback:
            Called for every property update that has no per-key callback
            registered.  Signature: ``(key, old_value, new_value) -> None``.
    """

    def __init__(
        self,
        config_file_path: str,
        unix_socket_path: Optional[str] = None,
        *,
        repository: Optional[PropertyRepository] = None,
        default_callback: UpdateCallback = _noop_callback,
    ) -> None:
        self._config_file_path = config_file_path
        self._unix_socket_path: str = unix_socket_path or os.environ.get(
            _ENV_SOCKET_PATH, ""
        )
        self._repository: PropertyRepository = (
            repository if repository is not None else InMemoryPropertyRepository()
        )
        self._default_callback: UpdateCallback = default_callback

        self._callbacks: dict[str, UpdateCallback] = {}
        self._callbacks_lock = threading.RLock()

        self._listener_lock = threading.Lock()
        self._listener_running = False

    # ------------------------------------------------------------------
    # Public API
    # ------------------------------------------------------------------

    def init(self) -> None:
        """Load the JSON config file then start the socket listener.

        The file is loaded first so that properties are available even when
        the socket cannot be bound.

        Raises:
            ValueError: If the config file is empty or cannot be parsed.
            OSError: If the config file cannot be opened.
        """
        self._read_from_file()
        try:
            self._start_listener()
        except OSError as exc:
            logger.error("cms: failed to start socket listener: %s", exc)

    def get(self, key: str) -> Optional[str]:
        """Return the current value for *key*, or ``None`` if not present."""
        return self._repository.get_by_key(key)

    def add_update_callback(self, key: str, callback: UpdateCallback) -> None:
        """Register *callback* to be invoked whenever *key* is updated.

        Replaces any previously registered callback for the same key.
        """
        with self._callbacks_lock:
            self._callbacks[key] = callback

    def remove_update_callback(self, key: str) -> None:
        """Remove the per-key callback for *key* (no-op if none was registered)."""
        with self._callbacks_lock:
            self._callbacks.pop(key, None)

    # ------------------------------------------------------------------
    # Internal: config file
    # ------------------------------------------------------------------

    def _read_from_file(self) -> None:
        path = self._config_file_path
        try:
            with open(path, "rb") as fh:
                raw = fh.read()
        except OSError as exc:
            raise OSError(f"Failed to read config file '{path}': {exc}") from exc

        if not raw.strip():
            raise ValueError(f"Config file is blank: '{path}'")

        try:
            values = json.loads(raw)
        except json.JSONDecodeError as exc:
            raise ValueError(f"Failed to parse JSON from '{path}': {exc}") from exc

        if not isinstance(values, dict):
            raise ValueError(
                f"Config file '{path}' must contain a JSON object at the top level"
            )

        data = cast(dict[Any, Any], values)
        for key, value in data.items():
            if not isinstance(key, str):
                raise ValueError(
                    f"Config file '{path}' must use string object keys; got {type(key)!r}"
                )
            self._store(key, _json_value_to_storage_string(value))

    # ------------------------------------------------------------------
    # Internal: socket listener
    # ------------------------------------------------------------------

    def _start_listener(self) -> None:
        if not self._unix_socket_path:
            logger.warning(
                "cms: no socket path configured (set CMS_UNIX_SOCKET_PATH or pass "
                "unix_socket_path).  Socket listener not started."
            )
            return

        with self._listener_lock:
            if self._listener_running:
                return

            sock_path = self._unix_socket_path
            sock_dir = os.path.dirname(sock_path)
            if sock_dir:
                os.makedirs(sock_dir, exist_ok=True)

            # Remove a stale socket file if present.
            try:
                os.unlink(sock_path)
            except FileNotFoundError:
                pass

            srv = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            try:
                srv.bind(sock_path)
                srv.listen()
            except OSError:
                srv.close()
                raise

            self._listener_running = True
            logger.info("cms: listening on socket %s", sock_path)

            t = threading.Thread(
                target=self._accept_loop,
                args=(srv, sock_path),
                daemon=True,
                name="cms-socket-listener",
            )
            t.start()

    def _accept_loop(self, srv: socket.socket, sock_path: str) -> None:
        try:
            while True:
                try:
                    conn, _ = srv.accept()
                except OSError as exc:
                    logger.error("cms: socket accept error: %s", exc)
                    break
                t = threading.Thread(
                    target=self._handle_connection,
                    args=(conn,),
                    daemon=True,
                    name="cms-conn-handler",
                )
                t.start()
        finally:
            srv.close()
            try:
                os.unlink(sock_path)
            except OSError:
                pass
            with self._listener_lock:
                self._listener_running = False

    def _handle_connection(self, conn: socket.socket) -> None:
        with conn:
            reader = PropertyUpdateStreamReader(conn.makefile("rb"))
            while True:
                try:
                    msg = reader.read_message()
                except (EOFError, OSError, ValueError) as exc:
                    logger.debug("cms: stream ended: %s", exc)
                    break
                if msg is None:
                    break  # clean EOF
                self._store(msg.key, msg.value.decode("utf-8", errors="replace"))

    # ------------------------------------------------------------------
    # Internal: store + dispatch callbacks
    # ------------------------------------------------------------------

    def _store(self, key: str, new_value: Optional[str]) -> None:
        old_value = self._repository.store(key, new_value)
        with self._callbacks_lock:
            cb = self._callbacks.get(key)
        try:
            if cb is not None:
                cb(key, old_value, new_value)
            else:
                self._default_callback(key, old_value, new_value)
        except Exception:
            logger.exception("cms: callback threw for key '%s'", key)
