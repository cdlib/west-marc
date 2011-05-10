@echo off
rem ---------------------------------------------------------------------------
rem
rem Wrapper for diff to redirect the output when run from perl.
rem
rem ---------------------------------------------------------------------------

if '%2' == '' goto :badparms
if '%3' == '' goto :noredir

rem -------------------------------------------------------------------------------
rem We have at least three arguments.
rem Use all but the last to build the parameter string to send to diff,
rem and use the last the target for the diff output.
rem -------------------------------------------------------------------------------
setlocal

:getargs
if '%2' == '' goto :gotargs
set args=%args% %1
shift
goto :getargs

:gotargs
diff %args% > %1
endlocal
goto :done

rem ---------------------------------------------------------------------------
rem If we have exactly two arguments run diff with them and exit
rem ---------------------------------------------------------------------------
:noredir
echo 'diff %1 %2'
rem diff %1 %2
goto :done


rem ---------------------------------------------------------------------------
rem If we don't have at least two arguments issue an error message and quit
rem ---------------------------------------------------------------------------
:badparms
echo At least two arguments must be specified
goto :done

:done
