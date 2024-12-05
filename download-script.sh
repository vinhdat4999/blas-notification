#!/bin/bash

GH_REPO="https://api.github.com/repos/vinhdat4999/blas-opentelemetry-agent-extension/releases/tags/v${BLAS_OTEL_EXTENSION_VERSION}"

RELEASE_RESPONSE=$(curl -s -H "Authorization: Bearer ${PACKAGE}" "$GH_REPO")

ASSET_URL=$(echo "$RELEASE_RESPONSE" | jq -r '.assets[] | select(.name | endswith(".jar")) | .url')

if [ -z "$ASSET_URL" ]; then
  echo "Error: Unable to find JAR asset in release v${BLAS_OTEL_EXTENSION_VERSION}"
  exit 1
fi

# Download the extension JAR
echo "Downloading blas-opentelemetry-agent-extension.jar..."
if ! curl -L -H "Authorization: Bearer ${PACKAGE}" \
          -H "Accept: application/octet-stream" \
          -o blas-opentelemetry-agent-extension.jar \
          "$ASSET_URL"; then
  echo "Error: Failed to download blas-opentelemetry-agent-extension.jar"
  exit 1
fi

# Download the OpenTelemetry Java agent
echo "Downloading OpenTelemetry Java agent version v${OTEL_AGENT_VERSION}..."
OTEL_AGENT_URL="https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar"

if ! curl -sSLo opentelemetry-javaagent.jar "$OTEL_AGENT_URL"; then
  echo "Error: Failed to download opentelemetry-javaagent.jar"
  exit 1
fi

echo "Download complete!"
