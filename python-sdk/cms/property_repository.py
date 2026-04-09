from __future__ import annotations

import threading
from abc import ABC, abstractmethod
from typing import Optional


class PropertyRepository(ABC):

    @abstractmethod
    def get_by_key(self, key: str) -> Optional[str]: ...

    @abstractmethod
    def store(self, key: str, new_value: Optional[str]) -> Optional[str]: ...


class InMemoryPropertyRepository(PropertyRepository):

    def __init__(self) -> None:
        self._lock = threading.RLock()
        self._properties: dict[str, str] = {}

    def get_by_key(self, key: str) -> Optional[str]:
        with self._lock:
            return self._properties.get(key)

    def store(self, key: str, new_value: Optional[str]) -> Optional[str]:
        with self._lock:
            old = self._properties.get(key)
            if new_value is None:
                self._properties.pop(key, None)
            else:
                self._properties[key] = new_value
            return old
