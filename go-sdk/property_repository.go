package cms

import "sync"

// PropertyRepository stores property values as UTF-8 text, mirroring the Java SDK.
type PropertyRepository interface {
	// GetByKey returns nil if the key is absent (including when the value was never set).
	GetByKey(key string) *string

	// Store sets the value for key when newValue is non-nil, or removes the key when newValue is nil.
	// It returns the previous value, or nil if the key was absent.
	Store(key string, newValue *string) *string
}

type InMemoryPropertyRepository struct {
	mu         sync.RWMutex
	properties map[string]string
}

func NewInMemoryPropertyRepository() *InMemoryPropertyRepository {
	return &InMemoryPropertyRepository{
		properties: make(map[string]string),
	}
}

func (r *InMemoryPropertyRepository) GetByKey(key string) *string {
	r.mu.RLock()
	defer r.mu.RUnlock()
	v, ok := r.properties[key]
	if !ok {
		return nil
	}
	return &v
}

func (r *InMemoryPropertyRepository) Store(key string, newValue *string) *string {
	r.mu.Lock()
	defer r.mu.Unlock()
	old, hadOld := r.properties[key]
	var oldPtr *string
	if hadOld {
		o := old
		oldPtr = &o
	}
	if newValue == nil {
		delete(r.properties, key)
	} else {
		r.properties[key] = *newValue
	}
	return oldPtr
}
