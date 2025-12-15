@echo off
echo Running NES Verification...
java -cp target/classes Main --verify
if %ERRORLEVEL% EQU 0 (
    echo.
    echo ---------------------------------------
    echo [SUCCESS] Verification Passed!
    echo ---------------------------------------
) else (
    echo.
    echo ---------------------------------------
    echo [FAILURE] Verification Failed!
    echo ---------------------------------------
)
pause
