package cms

import (
	"encoding/binary"
	"fmt"
	"io"

	"google.golang.org/protobuf/encoding/protowire"
)

type PropertyUpdateMessage struct {
	Key   string
	Value []byte
}

type PropertyUpdateStreamReader struct {
	r io.Reader
}

func NewPropertyUpdateStreamReader(r io.Reader) *PropertyUpdateStreamReader {
	return &PropertyUpdateStreamReader{r: r}
}

func (s *PropertyUpdateStreamReader) ReadMessage() (*PropertyUpdateMessage, error) {
	payloadLen, err := s.readUint32()
	if err == io.EOF || err == io.ErrUnexpectedEOF {
		// Clean EOF at the start of a new message — stream has ended normally.
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("reading payload length: %w", err)
	}

	payload, err := s.readExactly(int(payloadLen))
	if err != nil {
		return nil, fmt.Errorf("reading payload (%d bytes): %w", payloadLen, err)
	}

	key, value, err := parsePropertyPayload(payload)
	if err != nil {
		return nil, fmt.Errorf("parsing protobuf Property payload: %w", err)
	}

	return &PropertyUpdateMessage{
		Key:   key,
		Value: value,
	}, nil
}

func (s *PropertyUpdateStreamReader) readUint32() (uint32, error) {
	var v uint32
	err := binary.Read(s.r, binary.BigEndian, &v)
	return v, err
}

func (s *PropertyUpdateStreamReader) readExactly(n int) ([]byte, error) {
	buf := make([]byte, n)
	_, err := io.ReadFull(s.r, buf)
	return buf, err
}

// parsePropertyPayload decodes com.fyordo.cms.Property:
//
//	1: string key
//	2: bytes  value
//
// It intentionally parses only the fields needed by the Go SDK without relying
// on generated protobuf types.
func parsePropertyPayload(payload []byte) (key string, value []byte, err error) {
	for len(payload) > 0 {
		num, typ, n := protowire.ConsumeTag(payload)
		if n < 0 {
			return "", nil, protowire.ParseError(n)
		}
		payload = payload[n:]

		switch num {
		case 1: // key
			if typ != protowire.BytesType {
				n = protowire.ConsumeFieldValue(num, typ, payload)
				if n < 0 {
					return "", nil, protowire.ParseError(n)
				}
				payload = payload[n:]
				continue
			}
			var b []byte
			b, n = protowire.ConsumeBytes(payload)
			if n < 0 {
				return "", nil, protowire.ParseError(n)
			}
			key = string(b)
			payload = payload[n:]

		case 2: // value
			if typ != protowire.BytesType {
				n = protowire.ConsumeFieldValue(num, typ, payload)
				if n < 0 {
					return "", nil, protowire.ParseError(n)
				}
				payload = payload[n:]
				continue
			}
			var b []byte
			b, n = protowire.ConsumeBytes(payload)
			if n < 0 {
				return "", nil, protowire.ParseError(n)
			}

			value = append([]byte(nil), b...)
			payload = payload[n:]

		default:
			n = protowire.ConsumeFieldValue(num, typ, payload)
			if n < 0 {
				return "", nil, protowire.ParseError(n)
			}
			payload = payload[n:]
		}
	}

	return key, value, nil
}
