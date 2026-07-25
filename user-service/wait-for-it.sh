#!/bin/bash
# wait-for-it.sh

set -e

# Parse arguments: host port [timeout] -- command
host="$1"
port="$2"
shift 2

# Extract timeout if provided (format: --timeout=value or --timeout value)
timeout=30
if [[ "$1" =~ ^--timeout=([0-9]+)$ ]]; then
  timeout="${BASH_REMATCH[1]}"
  shift
elif [[ "$1" == "--timeout" ]]; then
  shift
  if [[ "$1" =~ ^[0-9]+$ ]]; then
    timeout="$1"
    shift
  fi
fi

# Check if -- separator exists
if [[ "$1" == "--" ]]; then
  shift
fi

echo "Waiting for $host:$port..."
echo "Timeout: $timeout"
echo "Remaining args: $@"
echo "Number of args: $#"

while ! nc -z "$host" "$port" 2>/dev/null; do
  timeout=$((timeout - 1))
  if [ $timeout -le 0 ]; then
    echo "Timeout waiting for $host:$port"
    exit 1
  fi
  echo "Waiting for $host:$port... ($timeout seconds left)"
  sleep 1
done

echo "$host:$port is up, starting application..."

# Execute the command with all remaining arguments
exec "$@"
