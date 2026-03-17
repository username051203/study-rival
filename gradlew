#!/usr/bin/env sh
APP_HOME="$(cd "$(dirname "$0")" && pwd -P)"
CLASSPATH="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -s "$CLASSPATH" ]; then
    exec java -classpath "$CLASSPATH" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
    exec gradle "$@"
fi
echo "ERROR: gradle not found"
exit 1
