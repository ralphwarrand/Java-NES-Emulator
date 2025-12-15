@echo off
set "ROM=%1"
if "%ROM%"=="" set "ROM=resources/nestest.nes"

REM Compile first to ensure latest changes (optional, but good for dev)
if not exist target\classes\Main.class (
    echo Compiling...
    javac -d target/classes -sourcepath src/main/java src/main/java/Main.java
)

echo Running Java NES Emulator with ROM: %ROM%
java -cp target/classes Main "%ROM%"
pause
