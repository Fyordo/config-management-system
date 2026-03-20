"""Thread-safe in-memory property storage."""

from __future__ import annotations

import threading
from abc import ABC, abstractmethod
from typing import Any, Optional


class PropertyRepository(ABC):
    """Interface for key/value property storage."""

    @abstractmethod
    def get_by_key(self, key: str) -> Optional[Any]:
        """Return the stored value for *key*, or ``None`` if absent."""

    @abstractmethod
    def store(self, key: str, new_value: Optional[Any]) -> Optional[Any]:
        """Store *new_value* under *key*.

        If *new_value* is ``None`` the key is removed.
        Returns the previous value (or ``None`` if the key was absent).
        """


class InMemoryPropertyRepository(PropertyRepository):
    """Concurrency-safe in-memory implementation backed by a plain ``dict``."""

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._properties: dict[str, Any] = {}

    def get_by_key(self, key: str) -> Optional[Any]:
        with self._lock:
            return self._properties.get(key)

    def store(self, key: str, new_value: Optional[Any]) -> Optional[Any]:
        with self._lock:
            old = self._properties.get(key)
            if new_value is None:
                self._properties.pop(key, None)
            else:
                self._properties[key] = new_value
            return old
