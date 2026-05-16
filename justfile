default:
    @just --list

# --- Build ---

build-debug:
    ./gradlew assembleTempusDebug assembleDegoogledDebug

build-release:
    ./gradlew assembleTempusRelease assembleDegoogledRelease

build-all: build-debug build-release

# --- Test ---

test:
    ./gradlew test

test-instrumented:
    ./gradlew connectedAndroidTest

# --- Lint & Static Analysis ---

lint:
    ./gradlew lint

lint-report:
    ./gradlew lint && open app/build/reports/lint-results-tempusDebug.html 2>/dev/null || xdg-open app/build/reports/lint-results-tempusDebug.html

check: lint test

# --- Clean ---

clean:
    ./gradlew clean

clean-build: clean build-debug

# --- APK output helpers ---

apks:
    @find app/build/outputs/apk -name '*.apk' 2>/dev/null | sort || echo "No APKs found — run build first"

# --- Dependency management ---

deps:
    ./gradlew dependencies --configuration releaseRuntimeClasspath

deps-updates:
    ./gradlew dependencyUpdates

# --- Gradle wrapper ---

wrapper-upgrade version:
    ./gradlew wrapper --gradle-version={{ version }}

# --- Utility ---

tasks:
    ./gradlew tasks --all

gradle-properties:
    ./gradlew properties
