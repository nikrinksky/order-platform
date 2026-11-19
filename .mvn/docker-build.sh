#!/bin/sh
# Builds one module inside the Docker build stage.
#
# The mirror configured in docker-settings.xml occasionally drops a connection in the middle of a
# large jar ("Premature end of Content-Length delimited message body"). Maven treats that as a hard
# failure, so the image build died on a download that would have succeeded a second later, and the
# whole pipeline failed on a network hiccup rather than on the code. The build is therefore
# repeated: artifacts that already arrived stay in the BuildKit cache mount at /root/.m2, so a
# retry only fetches what is still missing.
set -eu

module="${1:?usage: docker-build.sh <module artifactId>}"
attempts="${MAVEN_BUILD_ATTEMPTS:-3}"

# A slow mirror is not the same as a broken one: let the connection stay idle longer and let the
# resolver retry the requests it knows how to retry.
MAVEN_OPTS="${MAVEN_OPTS:-} -Daether.connector.http.retryHandler.count=5 -Daether.connector.http.connectTimeout=60000 -Daether.connector.http.requestTimeout=300000"
export MAVEN_OPTS

attempt=1
while [ "$attempt" -le "$attempts" ]; do
    echo "=== Building ${module} (attempt ${attempt} of ${attempts}) ==="
    if mvn -B -s /workspace/.mvn/docker-settings.xml -pl "${module}" -am -DskipTests package; then
        exit 0
    fi
    echo "=== Attempt ${attempt} of ${attempts} failed ===" >&2
    attempt=$((attempt + 1))
    if [ "$attempt" -le "$attempts" ]; then
        # The mirror needs a moment before it serves the same artifact again
        sleep 10
    fi
done

echo "=== Building ${module} failed after ${attempts} attempts ===" >&2
exit 1
