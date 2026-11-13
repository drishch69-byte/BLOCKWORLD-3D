#!/usr/bin/env bash
set -u
cd "$(dirname "$0")"
mkdir -p .logs
LOG_FILE="$PWD/.logs/latest-run.log"
MAVEN_OPTS="${MAVEN_OPTS:-} -XX:ErrorFile=.logs/hs_err_pid%p.log -XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=.logs"
export MAVEN_OPTS
{
  echo "============================================================"
  echo "CUBEVERSE 3D 1.9.2 - CLASSIC HOTBAR CONTROLS"
  date
  echo "Project: $PWD"
  echo "============================================================"
  java -version
} > "$LOG_FILE" 2>&1

if ! command -v java >/dev/null 2>&1; then
  echo "Java was not found. Install JDK 17 or newer." | tee -a "$LOG_FILE"
  EXIT_CODE=1
elif command -v mvn >/dev/null 2>&1; then
  mvn -B -Dstyle.color=never clean compile exec:java >> "$LOG_FILE" 2>&1
  EXIT_CODE=$?
elif [[ -x ".tools/apache-maven-3.9.11/bin/mvn" ]]; then
  .tools/apache-maven-3.9.11/bin/mvn -B -Dstyle.color=never clean compile exec:java >> "$LOG_FILE" 2>&1
  EXIT_CODE=$?
else
  echo "Maven 3.9+ is required. Install Maven and rerun ./run.sh." | tee -a "$LOG_FILE"
  EXIT_CODE=1
fi

echo "
CUBEVERSE exited with code $EXIT_CODE. Log: $LOG_FILE"
tail -n 70 "$LOG_FILE"
for crash_log in .logs/hs_err_pid*.log; do
  [ -e "$crash_log" ] && echo "JVM FATAL REPORT: $crash_log"
done
for heap_dump in .logs/java_pid*.hprof; do
  [ -e "$heap_dump" ] && echo "HEAP DUMP: $heap_dump"
done
read -r -p "Press Enter to close this terminal... " _ || true
exit "$EXIT_CODE"
