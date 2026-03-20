package cms

import (
	"encoding/binary"
	"fmt"
	"io"
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
	keyLen, err := s.readUint32()
	if err == io.EOF || err == io.ErrUnexpectedEOF {
		// Clean EOF at the start of a new message — stream has ended normally.
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("reading key length: %w", err)
	}

	keyBytes, err := s.readExactly(int(keyLen))
	if err != nil {
		return nil, fmt.Errorf("reading key (%d bytes): %w", keyLen, err)
	}

	valueLen, err := s.readUint32()
	if err != nil {
		return nil, fmt.Errorf("unexpected EOF after key (expected value length): %w", err)
	}

	valueBytes, err := s.readExactly(int(valueLen))
	if err != nil {
		return nil, fmt.Errorf("reading value (%d bytes): %w", valueLen, err)
	}

	return &PropertyUpdateMessage{
		Key:   string(keyBytes),
		Value: valueBytes,
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
