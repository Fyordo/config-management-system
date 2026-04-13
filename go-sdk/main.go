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
//	pm.AddUpdateCallback("feature.flag", func(key string, old, new *string) {
//	    log.Printf("property %q updated", key)
//	})
//
//	if err := pm.Init(); err != nil {
//	    log.Fatal(err)
//	}
//
//	pm.Set("feature.flag", "on")
//	pm.Delete("feature.flag") // or Store("feature.flag", nil)
//
//	value := pm.Get("feature.flag") // *string; nil if missing
package cms
