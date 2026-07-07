@echo off
setlocal

//cmake-4.4.0-rc3-windows-x86_64.zip setup
//llvm-mingw-20240619-ucrt-x86_64.zip

REM ============================================================
REM Build vecunative.dll (Windows)
REM ============================================================

cd /d "%~dp0"

REM ------------------------------------------------------------------
REM JAVA_HOME
REM ------------------------------------------------------------------

if "%JAVA_HOME%"=="" (
    echo ERROR: JAVA_HOME is not set.
    exit /b 1
)

echo [buildNative] JAVA_HOME=%JAVA_HOME%

if not exist "%JAVA_HOME%\include\jni.h" (
    echo ERROR: jni.h not found under %JAVA_HOME%\include
    exit /b 1
)

REM ------------------------------------------------------------------
REM LLVM-MinGW
REM ------------------------------------------------------------------

set LLVM=C:\llvm-mingw

set PATH=%LLVM%\bin;%PATH%

REM ------------------------------------------------------------------
REM Clean previous build
REM ------------------------------------------------------------------

if exist build (
    rmdir /S /Q build
)

REM ------------------------------------------------------------------
REM Configure
REM ------------------------------------------------------------------

cmake ^
-S . ^
-B build ^
-G "MinGW Makefiles" ^
-DCMAKE_BUILD_TYPE=Release ^
-DCMAKE_C_COMPILER=%LLVM%\bin\x86_64-w64-mingw32-gcc.exe ^
-DCMAKE_CXX_COMPILER=%LLVM%\bin\x86_64-w64-mingw32-g++.exe

if errorlevel 1 exit /b 1

REM ------------------------------------------------------------------
REM Build
REM ------------------------------------------------------------------

cmake --build build --parallel

if errorlevel 1 exit /b 1

echo.
echo ===========================================
echo Build completed successfully.
echo Output:
echo %CD%\build
echo ===========================================

dir build