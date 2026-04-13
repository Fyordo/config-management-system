package cms

import (
	"testing"
)

func TestGetByKey_MissingKeyReturnsNil(t *testing.T) {
	repo := NewInMemoryPropertyRepository()
	if got := repo.GetByKey("absent"); got != nil {
		t.Fatalf("expected nil, got %q", *got)
	}
}

func TestStoreAndRetrieve(t *testing.T) {
	repo := NewInMemoryPropertyRepository()
	repo.Store("k", StringRef("v"))
	got := repo.GetByKey("k")
	if got == nil || *got != "v" {
		t.Fatalf("expected %q, got %v", "v", got)
	}
}

func TestStoreReturnsOldValue(t *testing.T) {
	repo := NewInMemoryPropertyRepository()

	old1 := repo.Store("k", StringRef("first"))
	if old1 != nil {
		t.Fatalf("expected nil on first store, got %q", *old1)
	}

	old2 := repo.Store("k", StringRef("second"))
	if old2 == nil || *old2 != "first" {
		t.Fatalf("expected %q, got %v", "first", old2)
	}
}

func TestStoreNilRemovesKey(t *testing.T) {
	repo := NewInMemoryPropertyRepository()
	repo.Store("k", StringRef("value"))

	old := repo.Store("k", nil)
	if old == nil || *old != "value" {
		t.Fatalf("expected %q from delete, got %v", "value", old)
	}
	if got := repo.GetByKey("k"); got != nil {
		t.Fatalf("expected nil after delete, got %q", *got)
	}
}

func TestStoreOverwrite(t *testing.T) {
	repo := NewInMemoryPropertyRepository()
	repo.Store("k", StringRef("a"))
	repo.Store("k", StringRef("b"))

	got := repo.GetByKey("k")
	if got == nil || *got != "b" {
		t.Fatalf("expected %q after overwrite, got %v", "b", got)
	}
}

func TestStoreMultipleKeys(t *testing.T) {
	repo := NewInMemoryPropertyRepository()
	repo.Store("k1", StringRef("v1"))
	repo.Store("k2", StringRef("v2"))
	repo.Store("k3", StringRef("v3"))

	for _, tc := range []struct{ key, want string }{
		{"k1", "v1"}, {"k2", "v2"}, {"k3", "v3"},
	} {
		got := repo.GetByKey(tc.key)
		if got == nil || *got != tc.want {
			t.Errorf("GetByKey(%q) = %v, want %q", tc.key, got, tc.want)
		}
	}
}
