package cms

import (
	"encoding/json"
	"fmt"
	"log"
	"net"
	"os"
	"path/filepath"
	"sync"
)

type PropertyUpdateCallback func(key string, oldValue, newValue *string)

type PropertyManager struct {
	mu              sync.RWMutex
	listenerMu      sync.Mutex
	repository      PropertyRepository
	callbacks       map[string]PropertyUpdateCallback
	defaultCallback PropertyUpdateCallback
	configFilePath  string
	unixSocketPath  string
	listenerRunning bool
}

func NewPropertyManager(
	repository PropertyRepository,
	configFilePath string,
	unixSocketPath string,
) *PropertyManager {
	return NewPropertyManagerWithCallback(
		repository,
		configFilePath,
		unixSocketPath,
		func(_ string, _, _ *string) {},
	)
}

func NewPropertyManagerWithCallback(
	repository PropertyRepository,
	configFilePath string,
	unixSocketPath string,
	defaultCallback PropertyUpdateCallback,
) *PropertyManager {
	return &PropertyManager{
		repository:      repository,
		callbacks:       make(map[string]PropertyUpdateCallback),
		defaultCallback: defaultCallback,
		configFilePath:  configFilePath,
		unixSocketPath:  unixSocketPath,
	}
}

func (pm *PropertyManager) Init() error {
	if err := pm.ReadFromFile(); err != nil {
		return err
	}
	pm.ListenSocket()
	return nil
}

func (pm *PropertyManager) ReadFromFile() error {
	data, err := os.ReadFile(pm.configFilePath)
	if err != nil {
		return fmt.Errorf("failed to read properties from file %s: %w", pm.configFilePath, err)
	}
	if len(data) == 0 {
		return fmt.Errorf("config file is blank: %s", pm.configFilePath)
	}

	var values map[string]json.RawMessage
	if err := json.Unmarshal(data, &values); err != nil {
		return fmt.Errorf("failed to parse JSON from file %s: %w", pm.configFilePath, err)
	}

	for key, raw := range values {
		s, err := jsonRawToStorageString(raw)
		if err != nil {
			return fmt.Errorf("failed to normalize property %q in %s: %w", key, pm.configFilePath, err)
		}
		pm.Set(key, s)
	}
	return nil
}

func (pm *PropertyManager) AddUpdateCallback(key string, callback PropertyUpdateCallback) {
	pm.mu.Lock()
	defer pm.mu.Unlock()
	pm.callbacks[key] = callback
}

func (pm *PropertyManager) Get(key string) *string {
	return pm.repository.GetByKey(key)
}

// Set stores value for key and runs the same callback logic as Store.
// Prefer Set over Store when you have a plain string (avoids &value pitfalls in loops).
func (pm *PropertyManager) Set(key, value string) {
	pm.Store(key, StringRef(value))
}

// Delete removes key from storage (equivalent to Store(key, nil)).
func (pm *PropertyManager) Delete(key string) {
	pm.Store(key, nil)
}

// Store sets or removes a property. Pass newValue == nil to delete the key; otherwise use Set or StringRef
// when you only have a string and do not want to manage pointers yourself.
func (pm *PropertyManager) Store(key string, newValue *string) {
	oldValue := pm.repository.Store(key, newValue)

	pm.mu.RLock()
	cb, ok := pm.callbacks[key]
	pm.mu.RUnlock()

	if ok && cb != nil {
		cb(key, oldValue, newValue)
	} else {
		pm.defaultCallback(key, oldValue, newValue)
	}
}

func (pm *PropertyManager) ListenSocket() {
	pm.listenerMu.Lock()
	defer pm.listenerMu.Unlock()

	if pm.listenerRunning {
		return
	}

	dir := filepath.Dir(pm.unixSocketPath)
	if dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			log.Printf("cms: failed to create socket directory %s: %v", dir, err)
			return
		}
	}

	// Remove a stale socket file left from a previous run.
	if err := os.Remove(pm.unixSocketPath); err != nil && !os.IsNotExist(err) {
		log.Printf("cms: failed to remove stale socket %s: %v", pm.unixSocketPath, err)
	}

	ln, err := net.Listen("unix", pm.unixSocketPath)
	if err != nil {
		log.Printf("cms: failed to listen on socket %s: %v", pm.unixSocketPath, err)
		return
	}

	pm.listenerRunning = true
	log.Printf("cms: listening on socket %s", pm.unixSocketPath)

	go func() {
		defer func() {
			err := ln.Close()
			if err != nil {
				log.Printf("cms: Failed to close listener")
				return
			}
			err = os.Remove(pm.unixSocketPath)
			if err != nil {
				log.Printf("cms: Failed to remove socket %s", pm.unixSocketPath)
				return
			}
			pm.listenerMu.Lock()
			pm.listenerRunning = false
			pm.listenerMu.Unlock()
		}()

		for {
			conn, err := ln.Accept()
			if err != nil {
				log.Printf("cms: socket accept error: %v", err)
				return
			}

			pm.processConn(conn)
		}
	}()
}

func (pm *PropertyManager) processConn(conn net.Conn) {
	defer func(conn net.Conn) {
		err := conn.Close()
		if err != nil {
			log.Printf("cms: failed to close connection: %v", err)
			return
		}
	}(conn)
	reader := NewPropertyUpdateStreamReader(conn)
	for {
		msg, err := reader.ReadMessage()
		if err != nil {
			log.Printf("cms: stream error: %v", err)
			return
		}
		if msg == nil {
			return // clean EOF — agent closed the connection
		}
		pm.Set(msg.Key, string(msg.Value))
	}
}

// StringRef returns a pointer to a copy of s. Use with Store when you need an explicit *string
// (e.g. from another API). Safe with range variables; Set is enough for most call sites.
func StringRef(s string) *string {
	return &s
}

// jsonRawToStorageString decodes one JSON value and converts it to the canonical string stored in memory.
// JSON strings are kept as-is; numbers, booleans, arrays, and objects are serialized with json.Marshal.
func jsonRawToStorageString(raw json.RawMessage) (string, error) {
	raw = trimJSONWhitespace(raw)
	if len(raw) > 0 && raw[0] == '"' {
		var s string
		if err := json.Unmarshal(raw, &s); err != nil {
			return "", err
		}
		return s, nil
	}
	var v interface{}
	if err := json.Unmarshal(raw, &v); err != nil {
		return "", err
	}
	out, err := json.Marshal(v)
	if err != nil {
		return "", err
	}
	return string(out), nil
}

func trimJSONWhitespace(b []byte) []byte {
	for len(b) > 0 {
		switch b[0] {
		case ' ', '\n', '\r', '\t':
			b = b[1:]
		default:
			return b
		}
	}
	return b
}
