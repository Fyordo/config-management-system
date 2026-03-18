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

type PropertyUpdateCallback func(key string, oldValue, newValue interface{})

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
		func(_ string, _, _ interface{}) {},
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

	var values map[string]interface{}
	if err := json.Unmarshal(data, &values); err != nil {
		return fmt.Errorf("failed to parse JSON from file %s: %w", pm.configFilePath, err)
	}

	for key, value := range values {
		pm.Store(key, value)
	}
	return nil
}

func (pm *PropertyManager) AddUpdateCallback(key string, callback PropertyUpdateCallback) {
	pm.mu.Lock()
	defer pm.mu.Unlock()
	pm.callbacks[key] = callback
}

func (pm *PropertyManager) Get(key string) interface{} {
	return pm.repository.GetByKey(key)
}

func (pm *PropertyManager) Store(key string, newValue interface{}) {
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
		pm.Store(msg.Key, msg.Value)
	}
}
