@echo off
rem
rem Run ant with the specifed target. If no target is specified,
rem issue an error message, and quit
rem

if not "%1" == "" goto :gottarget
echo "No ant target specified"
goto :done

:gottarget
if "%MB_HOME%" == "" set MB_HOME=.
echo MB_HOME=%MB_HOME%

if not exist setupdone.txt call setup.bat

pushd %MB_HOME%\scripts

setlocal
set ant_target=%1
echo "Running ant for target %ant_target%"

rem
rem set ANT_PARMS to include extra parameters.
rem In particular -debug will turn on debug messages.
rem

echo "call ant %ANT_PARMS% -buildfile build-marc.xml %ant_target% >%ant_target%.out.txt 2>&1"
call ant %ANT_PARMS% -buildfile build-marc.xml %ant_target% >%ant_target%.out.txt 2>&1

endlocal
popd

:done
