#!/bin/sh
# Maven Wrapper for Linux/Mac
# Usage: ./mvnw [args]
# Example: ./mvnw clean package -DskipTests

# Find project base directory
PRG="$0"
while [ -h "$PRG" ]; do
  ls=$(ls -ld "$PRG")
  link=$(expr "$ls" : '.*-> \(.*\)$')
  if expr "$link" : '/.*' > /dev/null; then
    PRG="$link"
  else
    PRG=$(dirname "$PRG")/"$link"
  fi
done
SAVED="$(pwd)"
cd "$(dirname "$PRG")/" >/dev/null
APP_HOME="$(pwd -P)"
cd "$SAVED" >/dev/null

# Use the Maven wrapper jar
JAVA_HOME="${JAVA_HOME:-$(dirname $(dirname $(readlink -f $(which java))))}"
exec "$JAVA_HOME/bin/java" \
  -classpath "$APP_HOME/.mvn/wrapper/maven-wrapper.jar" \
  -Dmaven.multiModuleProjectDirectory="$APP_HOME" \
  org.apache.maven.wrapper.MavenWrapperMain "$@"
