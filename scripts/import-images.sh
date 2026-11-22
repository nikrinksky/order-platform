# import-images.sh - loads the platform and infrastructure images into a k3d cluster.
#
# `k3d image import` looks like the obvious tool, but it silently fails on multi-arch images:
# docker save writes a manifest list whose per-platform manifests are not all in the tarball, and
# containerd answers "content digest ... not found". redpandadata/redpanda,
# confluentinc/cp-schema-registry and mailhog/mailhog are exactly such images, so the pods stayed
# in ContainerCreating while the kubelet tried to pull them from Docker Hub for half an hour.
#
# This script goes through containerd directly: docker save, copy the tarball into the node,
# `ctr images import`. It works for every image and needs no registry access.
#
# Usage: ./import-images.sh <cluster-name> <image> [image...]
set -eu

CLUSTER="${1:?usage: import-images.sh <cluster> <image> [image...]}"
shift

if [ "$#" -eq 0 ]; then
    echo "Nothing to import"
    exit 0
fi

# k3d names its containers k3d-<cluster>-server-0, k3d-<cluster>-agent-0, k3d-<cluster>-serverlb.
# The load balancer is an nginx container, not a k3s node: it has no containerd and no ctr.
NODES="$(k3d node list -o json \
    | tr ',' '\n' \
    | grep -o '"name":"[^"]*"' \
    | sed 's/"name":"//; s/"//' \
    | grep "^k3d-${CLUSTER}-" \
    | grep -v "serverlb" || true)"

if [ -z "$NODES" ]; then
    echo "No nodes found for cluster $CLUSTER"
    exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

for image in "$@"; do
    echo "Importing $image"
    tar_file="$TMP_DIR/image.tar"
    docker save -o "$tar_file" "$image"

    for node in $NODES; do
        docker cp "$tar_file" "$node:/tmp/import-image.tar"
        # docker save writes the manifest list, but the tarball only carries the blobs of the
        # current platform. Without --platform containerd tries to resolve every listed platform
        # and fails with "content digest ... not found".
        docker exec "$node" ctr -n k8s.io images import --platform linux/amd64 /tmp/import-image.tar >/dev/null
        docker exec "$node" rm -f /tmp/import-image.tar
    done

    rm -f "$tar_file"
done

echo "Imported $# image(s) into cluster $CLUSTER"
