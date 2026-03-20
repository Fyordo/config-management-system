// Package cms provides a client SDK for the Config Management System (CMS).
//
// It loads initial property values from a JSON configuration file and
// keeps them up to date by listening for binary update events on a UNIX
// domain socket sent by the CMS agent (see AGENT_CONTRACT.MD).
//
// # Quick start
//
//	repo := cms.NewInMemoryPropertyRepository()
//	pm := cms.NewPropertyManager(repo, "/etc/myapp/config.json", "/run/cms/cms.sock")
//
//	pm.AddUpdateCallback("feature.flag", func(key string, old, new interface{}) {
//	    log.Printf("property %q changed: %v -> %v", key, old, new)
//	})
//
//	if err := pm.Init(); err != nil {
//	    log.Fatal(err)
//	}
//
//	value := pm.Get("feature.flag")
package cms
