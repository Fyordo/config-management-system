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
    repo.store("k", "42")
    old = repo.store("k", None)
    assert old == "42"
    assert repo.get_by_key("k") is None


def test_store_strings():
    repo = InMemoryPropertyRepository()
    repo.store("a", "123")
    repo.store("b", "1.5")
    repo.store("c", "[1,2,3]")
    repo.store("d", '{"a":1}')
    repo.store("e", "\x00\xff")

    assert repo.get_by_key("a") == "123"
    assert repo.get_by_key("b") == "1.5"
    assert repo.get_by_key("c") == "[1,2,3]"
    assert repo.get_by_key("d") == '{"a":1}'
    assert repo.get_by_key("e") == "\x00\xff"


def test_store_large_string():
    repo = InMemoryPropertyRepository()
    data = (bytes(range(256)) * 4000).decode("latin-1")  # 1 MB round-trip as text
    repo.store("big", data)
    assert repo.get_by_key("big") == data
