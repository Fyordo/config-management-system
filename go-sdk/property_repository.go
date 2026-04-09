package cms

import "sync"

type PropertyRepository interface {
	GetByKey(key string) *string
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
