@echo off
REM ---------------------------------------------------------------------------
REM One-command build + run for beginners (Windows / cmd.exe).
REM Run from the repo root:  data\  must be writable here (auto-created on first run).
REM Requires JDK 8+ and Maven 3.6.3+ on PATH.
REM ---------------------------------------------------------------------------
call mvn -q clean package
if errorlevel 1 (
    echo Build failed. See messages above.
    exit /b 1
)
java -jar "target\wechat-ilink-bot-1.0.0-SNAPSHOT.jar"
