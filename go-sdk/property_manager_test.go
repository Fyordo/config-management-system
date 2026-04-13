package cms

import (
	"encoding/binary"
	"fmt"
	"net"
	"os"
	"path/filepath"
	"sync/atomic"
	"testing"
	"time"

	"google.golang.org/protobuf/encoding/protowire"
)

func testdataPath(name string) string {
	return filepath.Join("testdata", name)
}

var socketCounter atomic.Int64

// tmpSocketPath returns a short socket path in /tmp (macOS limits AF_UNIX paths to 104 bytes).
func tmpSocketPath(t *testing.T) string {
	t.Helper()
	n := socketCounter.Add(1)
	p := filepath.Join("/tmp", fmt.Sprintf("cms-test-%d-%d.sock", os.Getpid(), n))
	t.Cleanup(func() { os.Remove(p) })
	return p
}

// --- ReadFromFile ----------------------------------------------------------

func TestReadFromFile_BlankFile(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("empty.json"),
		tmpSocketPath(t),
	)
	if err := pm.ReadFromFile(); err == nil {
		t.Fatal("expected error for blank file")
	}
}

func TestReadFromFile_MissingFile(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("nonexistent.json"),
		tmpSocketPath(t),
	)
	if err := pm.ReadFromFile(); err == nil {
		t.Fatal("expected error for missing file")
	}
}

func TestReadFromFile_InvalidJSON(t *testing.T) {
	tmp := filepath.Join(t.TempDir(), "bad.json")
	os.WriteFile(tmp, []byte("{not valid}"), 0o644)

	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		tmp,
		tmpSocketPath(t),
	)
	if err := pm.ReadFromFile(); err == nil {
		t.Fatal("expected error for invalid JSON")
	}
}

func TestReadFromFile_ApplicationJSON(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
	)
	if err := pm.ReadFromFile(); err != nil {
		t.Fatalf("ReadFromFile: %v", err)
	}

	cases := []struct{ key, want string }{
		{"app.int.val", "123"},
		{"app.long.val", "123123123123"},
		{"app.string.val", "SomeRandomString"},
		{"app.list.val", "1,2,3,4"},
		{"app.enum.val", "TYPE_1"},
	}
	for _, tc := range cases {
		got := pm.Get(tc.key)
		if got == nil {
			t.Errorf("Get(%q) = nil, want %q", tc.key, tc.want)
		} else if *got != tc.want {
			t.Errorf("Get(%q) = %q, want %q", tc.key, *got, tc.want)
		}
	}
}

func TestReadFromFile_AllTypesNormalized(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("types.json"),
		tmpSocketPath(t),
	)
	if err := pm.ReadFromFile(); err != nil {
		t.Fatalf("ReadFromFile: %v", err)
	}

	cases := []struct{ key, want string }{
		{"str", "hello"},
		{"num", "42"},
		{"float", "3.14"},
		{"bool", "true"},
		{"arr", "[1,2,3]"},
		{"obj", `{"a":1}`},
	}
	for _, tc := range cases {
		got := pm.Get(tc.key)
		if got == nil {
			t.Errorf("Get(%q) = nil, want %q", tc.key, tc.want)
		} else if *got != tc.want {
			t.Errorf("Get(%q) = %q, want %q", tc.key, *got, tc.want)
		}
	}
}

func TestGetMissingKeyReturnsNil(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
	)
	if got := pm.Get("no.such.key"); got != nil {
		t.Fatalf("expected nil, got %q", *got)
	}
}

// --- Set / Delete / Store --------------------------------------------------

func TestSetAndGet(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
	)
	pm.Set("k", "v")
	got := pm.Get("k")
	if got == nil || *got != "v" {
		t.Fatalf("expected %q, got %v", "v", got)
	}
}

func TestDelete(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
	)
	pm.Set("k", "v")
	pm.Delete("k")
	if got := pm.Get("k"); got != nil {
		t.Fatalf("expected nil after Delete, got %q", *got)
	}
}

func TestStoreNilDeletesKey(t *testing.T) {
	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
	)
	pm.Set("k", "v")
	pm.Store("k", nil)
	if got := pm.Get("k"); got != nil {
		t.Fatalf("expected nil after Store(nil), got %q", *got)
	}
}

// --- Callbacks -------------------------------------------------------------

func TestDefaultCallbackInvoked(t *testing.T) {
	type event struct {
		key    string
		oldNil bool
		old    string
		newNil bool
		new    string
	}
	var events []event

	pm := NewPropertyManagerWithCallback(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
		func(key string, oldValue, newValue *string) {
			e := event{key: key}
			if oldValue == nil {
				e.oldNil = true
			} else {
				e.old = *oldValue
			}
			if newValue == nil {
				e.newNil = true
			} else {
				e.new = *newValue
			}
			events = append(events, e)
		},
	)

	pm.Set("k1", "v1")
	pm.Set("k1", "v2")

	if len(events) != 2 {
		t.Fatalf("expected 2 events, got %d", len(events))
	}

	e0 := events[0]
	if e0.key != "k1" || !e0.oldNil || e0.new != "v1" {
		t.Errorf("event[0] = %+v", e0)
	}

	e1 := events[1]
	if e1.key != "k1" || e1.old != "v1" || e1.new != "v2" {
		t.Errorf("event[1] = %+v", e1)
	}
}

func TestPerKeyCallbackOverridesDefault(t *testing.T) {
	defaultCalls := 0
	specificCalls := 0

	pm := NewPropertyManagerWithCallback(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
		func(_ string, _, _ *string) { defaultCalls++ },
	)
	pm.AddUpdateCallback("k1", func(_ string, _, _ *string) { specificCalls++ })

	pm.Set("k1", "v1")
	pm.Set("k2", "v2")

	if specificCalls != 1 {
		t.Errorf("specific callback: expected 1, got %d", specificCalls)
	}
	if defaultCalls != 1 {
		t.Errorf("default callback: expected 1, got %d", defaultCalls)
	}
}

func TestDeleteFiresCallbackWithNilNew(t *testing.T) {
	var received *string
	called := false

	pm := NewPropertyManagerWithCallback(
		NewInMemoryPropertyRepository(),
		testdataPath("application.json"),
		tmpSocketPath(t),
		func(_ string, _, newValue *string) {
			received = newValue
			called = true
		},
	)
	pm.Set("k", "v")
	called = false

	pm.Delete("k")
	if !called {
		t.Fatal("callback not called on Delete")
	}
	if received != nil {
		t.Fatalf("expected nil newValue on Delete, got %q", *received)
	}
}

// --- StringRef -------------------------------------------------------------

func TestStringRef(t *testing.T) {
	p := StringRef("hello")
	if p == nil || *p != "hello" {
		t.Fatalf("StringRef returned %v", p)
	}
}

// --- Socket integration ---------------------------------------------------

func encodeProtobufMessage(key string, value []byte) []byte {
	var payload []byte
	payload = protowire.AppendTag(payload, 1, protowire.BytesType)
	payload = protowire.AppendBytes(payload, []byte(key))
	payload = protowire.AppendTag(payload, 2, protowire.BytesType)
	payload = protowire.AppendBytes(payload, value)

	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, uint32(len(payload)))
	return append(header, payload...)
}

func TestSocketUpdatesStored(t *testing.T) {
	sockPath := tmpSocketPath(t)
	configPath := filepath.Join(t.TempDir(), "config.json")
	os.WriteFile(configPath, []byte(`{"a":"init"}`), 0o644)

	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		configPath,
		sockPath,
	)
	if err := pm.Init(); err != nil {
		t.Fatalf("Init: %v", err)
	}

	got := pm.Get("a")
	if got == nil || *got != "init" {
		t.Fatalf("after Init: Get(a) = %v, want %q", got, "init")
	}

	conn, err := net.Dial("unix", sockPath)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}

	msg1 := encodeProtobufMessage("a", []byte("updated"))
	msg2 := encodeProtobufMessage("b", []byte("new_val"))
	conn.Write(msg1)
	conn.Write(msg2)
	conn.Close()

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		v := pm.Get("b")
		if v != nil && *v == "new_val" {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}

	a := pm.Get("a")
	if a == nil || *a != "updated" {
		t.Errorf("Get(a) = %v, want %q", a, "updated")
	}
	b := pm.Get("b")
	if b == nil || *b != "new_val" {
		t.Errorf("Get(b) = %v, want %q", b, "new_val")
	}
}

func TestSocketCallbackFiresOnUpdate(t *testing.T) {
	sockPath := tmpSocketPath(t)
	configPath := filepath.Join(t.TempDir(), "config.json")
	os.WriteFile(configPath, []byte(`{}`), 0o644)

	type cbEvent struct {
		key string
		old *string
		new *string
	}
	var received []cbEvent

	pm := NewPropertyManager(
		NewInMemoryPropertyRepository(),
		configPath,
		sockPath,
	)
	pm.AddUpdateCallback("x", func(key string, old, new *string) {
		received = append(received, cbEvent{key, old, new})
	})

	if err := pm.Init(); err != nil {
		t.Fatalf("Init: %v", err)
	}

	conn, err := net.Dial("unix", sockPath)
	if err != nil {
		t.Fatalf("dial: %v", err)
	}
	conn.Write(encodeProtobufMessage("x", []byte("val")))
	conn.Close()

	deadline := time.Now().Add(2 * time.Second)
	for time.Now().Before(deadline) {
		if len(received) > 0 {
			break
		}
		time.Sleep(10 * time.Millisecond)
	}

	if len(received) != 1 {
		t.Fatalf("expected 1 callback event, got %d", len(received))
	}
	ev := received[0]
	if ev.key != "x" {
		t.Errorf("key = %q, want %q", ev.key, "x")
	}
	if ev.old != nil {
		t.Errorf("old = %q, want nil", *ev.old)
	}
	if ev.new == nil || *ev.new != "val" {
		t.Errorf("new = %v, want %q", ev.new, "val")
	}
}
