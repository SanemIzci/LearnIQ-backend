#!/bin/bash
# A helper script to run Maven commands with Java 21, avoiding Java 26 compatibility issues.

export JAVA_HOME=$(/usr/libexec/java_home -v 21)

echo "========================================================"
echo " Using Java version:"
java -version
echo "========================================================"

mvn "$@"
