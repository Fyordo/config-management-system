#!/bin/bash

set -e  # Exit on any error

mainVersion=$(grep "mainVersion=" build.number | cut -d'=' -f2)
majorVersion=$(grep "majorVersion=" build.number | cut -d'=' -f2)
minorVersion=$(grep "minorVersion=" build.number | cut -d'=' -f2)

if [[ -z "$mainVersion" || -z "$majorVersion" || -z "$minorVersion" ]]; then
    echo "Error: Could not read version components from build.number"
    exit 1
fi

minorVersion=$((minorVersion + 1))

updateDate=$(date '+%Y-%m-%d %H:%M:%S')

newVersion="${mainVersion}.${majorVersion}.${minorVersion}"

cat > build.number << EOF
updateDate=${updateDate}
mainVersion=${mainVersion}
majorVersion=${majorVersion}
minorVersion=${minorVersion}
EOF

echo "Version updated to: ${newVersion}"