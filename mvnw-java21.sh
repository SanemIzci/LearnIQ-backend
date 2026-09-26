#!/bin/bash
# A helper script to run Maven commands with Java 21, avoiding Java 26 compatibility issues.

export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export PATH="$JAVA_HOME/bin:$PATH"

echo "========================================================"
echo " Using Java version:"
java -version
echo "========================================================"

mvn "$@"
