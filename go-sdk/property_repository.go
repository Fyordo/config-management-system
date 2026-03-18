package cms

import "sync"

type PropertyRepository interface {
	GetByKey(key string) interface{}

	Store(key string, newValue interface{}) interface{}
}

type InMemoryPropertyRepository struct {
	mu         sync.RWMutex
	properties map[string]interface{}
}

func NewInMemoryPropertyRepository() *InMemoryPropertyRepository {
	return &InMemoryPropertyRepository{
		properties: make(map[string]interface{}),
	}
}

func (r *InMemoryPropertyRepository) GetByKey(key string) interface{} {
	r.mu.RLock()
	defer r.mu.RUnlock()
	return r.properties[key]
}

func (r *InMemoryPropertyRepository) Store(key string, newValue interface{}) interface{} {
	r.mu.Lock()
	defer r.mu.Unlock()
	old := r.properties[key]
	if newValue == nil {
		delete(r.properties, key)
	} else {
		r.properties[key] = newValue
	}
	return old
}
