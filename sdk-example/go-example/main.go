package main

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"os"

	cms "go-sdk"
)

func main() {
	configPath := envOr("CMS_PROPERTIES_FILE", "/app/config/application.json")
	socketPath := envOr("CMS_UNIX_SOCKET_PATH", "/app/config/cms.sock")
	addr := envOr("HTTP_ADDR", ":8080")

	pm := cms.NewPropertyManager(
		cms.NewInMemoryPropertyRepository(),
		configPath,
		socketPath,
	)

	pm.AddUpdateCallback("app.go.example", func(key string, oldValue, newValue *string) {
		fmt.Println("Property Callback !")
	})

	if err := pm.Init(); err != nil {
		log.Fatal("failed to init PropertyManager: %v", err)
	}

	http.HandleFunc("GET /test/property", makePropertyHandler(pm))

	log.Printf("listening on %s", addr)
	if err := http.ListenAndServe(addr, nil); err != nil {
		log.Fatalf("http server error: %v", err)
	}
}

func makePropertyHandler(pm *cms.PropertyManager) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		key := r.URL.Query().Get("key")
		if key == "" {
			http.Error(w, `{"error":"query parameter 'key' is required"}`, http.StatusBadRequest)
			return
		}

		value := pm.Get(key)
		if value == nil {
			http.Error(w, fmt.Sprintf(`{"error":"property '%s' not found"}`, key), http.StatusNotFound)
			return
		}

		w.Header().Set("Content-Type", "application/json")
		json.NewEncoder(w).Encode(map[string]interface{}{
			"key":   key,
			"value": *value,
		})
	}
}

func envOr(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}
