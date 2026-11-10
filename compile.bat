
@echo off
setlocal EnableExtensions EnableDelayedExpansion

:: Always run from the project directory
pushd "%~dp0"

set "targetdir=target"
set "srcdir=src"
set "libdir=lib"
set "resourcesdir=src\gui\buttonGraphics"

echo [INFO] Starting build process...

:: Create target directory
if not exist "%targetdir%" (
    mkdir "%targetdir%"
    if errorlevel 1 (
        echo [ERROR] Failed to create target directory.
        popd
        exit /b 1
    )
)

:: Build classpath
echo [INFO] Building classpath...
set "CLASSPATH=."

if exist "%libdir%" (
    for %%f in ("%libdir%\*.jar") do (
        if exist "%%~f" (
            set "CLASSPATH=!CLASSPATH!;%%~f"
        )
    )
)

:: Verify source directory
if not exist "%srcdir%" (
    echo [ERROR] Source directory not found.
    popd
    exit /b 1
)

:: Collect source files
echo [INFO] Collecting Java source files...

if exist sources.txt del sources.txt

for /r "%srcdir%" %%f in (*.java) do (
    set "filepath=%%~f"
    set "filepath=!filepath:\=/!"
    echo "!filepath!" >> sources.txt
)

if not exist sources.txt (
    echo [ERROR] No Java source files found.
    popd
    exit /b 1
)

for %%A in (sources.txt) do (
    if %%~zA EQU 0 (
        echo [ERROR] Source list is empty.
        del sources.txt
        popd
        exit /b 1
    )
)

:: Compile
echo [INFO] Compiling Java sources...

javac -sourcepath "%srcdir%" -d "%targetdir%" -cp "%CLASSPATH%" @sources.txt

if errorlevel 1 (
    echo [ERROR] Compilation failed.
    echo [INFO] sources.txt is preserved for debugging.
    popd
    exit /b 1
)

:: Remove source list after successful compilation
del sources.txt

:: Copy resources
if exist "%resourcesdir%" (
    echo [INFO] Copying resources...

    if not exist "%targetdir%\gui\buttonGraphics" (
        mkdir "%targetdir%\gui\buttonGraphics"
    )

    xcopy /y /q "%resourcesdir%\*" "%targetdir%\gui\buttonGraphics\" >nul

    if errorlevel 1 (
        echo [WARNING] Some resources may not have been copied.
    )
)

echo [INFO] Build completed successfully!

popd
endlocal
