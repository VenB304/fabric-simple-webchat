@echo off
echo Stopping Gradle Daemons...
call gradlew --stop

echo Cleaning build directory...
if exist build (
    rmdir /s /q build
)

echo Building...
call gradlew build

echo Done.
