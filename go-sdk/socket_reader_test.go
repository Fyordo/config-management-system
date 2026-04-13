package cms

import (
	"bytes"
	"encoding/binary"
	"testing"

	"google.golang.org/protobuf/encoding/protowire"
)

func buildMessage(key string, value []byte) []byte {
	var payload []byte
	payload = protowire.AppendTag(payload, 1, protowire.BytesType)
	payload = protowire.AppendBytes(payload, []byte(key))
	payload = protowire.AppendTag(payload, 2, protowire.BytesType)
	payload = protowire.AppendBytes(payload, value)

	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, uint32(len(payload)))
	return append(header, payload...)
}

func TestSingleMessageThenEOF(t *testing.T) {
	data := buildMessage("my.key", []byte("hello"))
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(data))

	msg, err := reader.ReadMessage()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if msg == nil {
		t.Fatal("expected message, got nil")
	}
	if msg.Key != "my.key" {
		t.Errorf("key = %q, want %q", msg.Key, "my.key")
	}
	if !bytes.Equal(msg.Value, []byte("hello")) {
		t.Errorf("value = %v, want %v", msg.Value, []byte("hello"))
	}

	eof, err := reader.ReadMessage()
	if err != nil {
		t.Fatalf("unexpected error on EOF: %v", err)
	}
	if eof != nil {
		t.Fatal("expected nil on EOF")
	}
}

func TestTwoMessagesSequential(t *testing.T) {
	data := append(
		buildMessage("k1", []byte("v1")),
		buildMessage("k2", []byte{0x00, 0x01, 0x02})...,
	)
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(data))

	m1, err := reader.ReadMessage()
	if err != nil {
		t.Fatal(err)
	}
	m2, err := reader.ReadMessage()
	if err != nil {
		t.Fatal(err)
	}
	eof, err := reader.ReadMessage()
	if err != nil {
		t.Fatal(err)
	}

	if m1 == nil || m1.Key != "k1" || !bytes.Equal(m1.Value, []byte("v1")) {
		t.Errorf("m1 = %+v", m1)
	}
	if m2 == nil || m2.Key != "k2" || !bytes.Equal(m2.Value, []byte{0x00, 0x01, 0x02}) {
		t.Errorf("m2 = %+v", m2)
	}
	if eof != nil {
		t.Error("expected nil on EOF")
	}
}

func TestEmptyValue(t *testing.T) {
	data := buildMessage("flag", []byte{})
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(data))

	msg, err := reader.ReadMessage()
	if err != nil {
		t.Fatal(err)
	}
	if msg == nil {
		t.Fatal("expected message")
	}
	if len(msg.Value) != 0 {
		t.Errorf("expected empty value, got %v", msg.Value)
	}
}

func TestBinaryValue(t *testing.T) {
	value := make([]byte, 256)
	for i := range value {
		value[i] = byte(i)
	}
	data := buildMessage("bin", value)
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(data))

	msg, err := reader.ReadMessage()
	if err != nil {
		t.Fatal(err)
	}
	if msg == nil {
		t.Fatal("expected message")
	}
	if !bytes.Equal(msg.Value, value) {
		t.Error("binary value mismatch")
	}
}

func TestCleanEOFOnEmptyStream(t *testing.T) {
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(nil))
	msg, err := reader.ReadMessage()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if msg != nil {
		t.Fatal("expected nil on empty stream")
	}
}

func TestTruncatedPayloadRaisesError(t *testing.T) {
	full := buildMessage("some.key", []byte("v"))
	truncated := full[:5]
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(truncated))

	_, err := reader.ReadMessage()
	if err == nil {
		t.Fatal("expected error for truncated payload")
	}
}

func TestEOFBetweenLengthAndPayload(t *testing.T) {
	header := make([]byte, 4)
	binary.BigEndian.PutUint32(header, 100)
	reader := NewPropertyUpdateStreamReader(bytes.NewReader(header))

	_, err := reader.ReadMessage()
	if err == nil {
		t.Fatal("expected error when payload is missing after length")
	}
}

func TestTruncatedLengthHeader(t *testing.T) {
	reader := NewPropertyUpdateStreamReader(bytes.NewReader([]byte{0x00, 0x01}))
	msg, err := reader.ReadMessage()
	if msg != nil && err == nil {
		t.Fatal("expected nil/error for truncated length header")
	}
}
