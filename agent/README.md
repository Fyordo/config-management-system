# CMS-Agent

## Compile
1. Run dev-container from `.devcontainer`
2. `mkdir build && cd build`
3. `cmake .. && cmake --build .`

## Preparation
```bash
export CMS_NAMESPACE=my-namespace
export CMS_SERVICE=my-service
export CMS_APPID=my-app-id
export CMS_SERVER_HOST=localhost:9090
export CMS_PROPERTIES_FILE=/path/to/properties.json
```

## Run
`./cmsagent` - runs agent on 50051 port

`./cmsagent 8080` - runs agent on 8080 port