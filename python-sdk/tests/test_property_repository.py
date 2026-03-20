"""Tests for InMemoryPropertyRepository."""

import pytest

from cms import InMemoryPropertyRepository


def test_get_missing_key_returns_none():
    repo = InMemoryPropertyRepository()
    assert repo.get_by_key("missing") is None


def test_store_and_retrieve():
    repo = InMemoryPropertyRepository()
    repo.store("key", "value")
    assert repo.get_by_key("key") == "value"


def test_store_returns_old_value():
    repo = InMemoryPropertyRepository()
    assert repo.store("k", "first") is None
    assert repo.store("k", "second") == "first"


def test_store_none_removes_key():
    repo = InMemoryPropertyRepository()
    repo.store("k", 42)
    old = repo.store("k", None)
    assert old == 42
    assert repo.get_by_key("k") is None


def test_store_various_types():
    repo = InMemoryPropertyRepository()
    repo.store("int", 123)
    repo.store("float", 1.5)
    repo.store("list", [1, 2, 3])
    repo.store("dict", {"a": 1})
    repo.store("bytes", b"\x00\xff")

    assert repo.get_by_key("int") == 123
    assert repo.get_by_key("float") == 1.5
    assert repo.get_by_key("list") == [1, 2, 3]
    assert repo.get_by_key("dict") == {"a": 1}
    assert repo.get_by_key("bytes") == b"\x00\xff"


def test_store_large_bytes():
    repo = InMemoryPropertyRepository()
    data = bytes(range(256)) * 4000  # 1 MB
    repo.store("big", data)
    assert repo.get_by_key("big") == data
