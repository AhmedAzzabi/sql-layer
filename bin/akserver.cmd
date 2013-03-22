@ECHO OFF

REM 
REM Copyright (C) 2009-2013 Akiban Technologies, Inc.
REM 
REM This program is free software: you can redistribute it and/or modify
REM it under the terms of the GNU Affero General Public License as published by
REM the Free Software Foundation, either version 3 of the License, or
REM (at your option) any later version.
REM 
REM This program is distributed in the hope that it will be useful,
REM but WITHOUT ANY WARRANTY; without even the implied warranty of
REM MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
REM GNU Affero General Public License for more details.
REM 
REM You should have received a copy of the GNU Affero General Public License
REM along with this program.  If not, see <http://www.gnu.org/licenses/>.
REM 

SETLOCAL

SET SERVER_JAR=akiban-server-1.5.2-SNAPSHOT.jar
SET SERVICE_NAME=akserver
SET SERVICE_DNAME=Akiban Server
SET SERVICE_DESC=Akiban Database Server

IF EXIST "%~dp0..\pom.xml" GOTO FROM_BUILD

REM Installation Configuration

FOR %%P IN ("%~dp0..") DO SET AKIBAN_HOME=%%~fP

SET JAR_FILE=%AKIBAN_HOME%\lib\%SERVER_JAR%
SET DEP_DIR=%AKIBAN_HOME%\lib\server
SET AKIBAN_CONF=%AKIBAN_HOME%
SET AKIBAN_LOGDIR=%AKIBAN_HOME%\log
SET AKIBAN_HOME_DIR=%AKIBAN_HOME%\lib

FOR %%P IN (prunsrv.exe) DO SET PRUNSRV=%%~$PATH:P
FOR %%P IN (prunmgr.exe) DO SET PRUNMGR=%%~$PATH:P
REM Not in path, assume installed with program.
IF "%PRUNSRV%"=="" (
  IF "%PROCESSOR_ARCHITECTURE%"=="x86" (
    SET PRUNSRV=%AKIBAN_HOME%\procrun\prunsrv
  ) ELSE (
    SET PRUNSRV=%AKIBAN_HOME%\procrun\%PROCESSOR_ARCHITECTURE%\prunsrv
) )
IF "%PRUNMGR%"=="" (
  SET PRUNMGR=%AKIBAN_HOME%\procrun\prunmgr
)

GOTO PARSE_CMD

:FROM_BUILD

REM Build Configuration

FOR %%P IN ("%~dp0..") DO SET BUILD_HOME=%%~fP

SET JAR_FILE=%BUILD_HOME%\target\%SERVER_JAR%
SET DEP_DIR=%BUILD_HOME%\target\dependency
SET AKIBAN_CONF=%BUILD_HOME%\conf
SET AKIBAN_LOGDIR=\tmp\akiban_server
SET AKIBAN_HOME_DIR=%BUILD_HOME%\target
SET PRUNSRV=prunsrv
SET PRUNMGR=prunmgr
SET SERVICE_MODE=manual

:PARSE_CMD

IF NOT DEFINED JVM_OPTS SET JVM_OPTS=-Dummy=

IF "%1"=="" GOTO USAGE

SET VERB=%1
SHIFT

:NEXT_OPT

IF "%1"=="" GOTO END_OPT

IF "%1"=="-j" (
  SET JAR_FILE=%2
  SHIFT
  SHIFT
) ELSE IF "%1"=="-d" (
  SET DEP_DIR=%2
  SHIFT
  SHIFT
) ELSE IF "%1"=="-c" (
  SET AKIBAN_CONF=%2
  SHIFT
  SHIFT
) ELSE IF "%1"=="-l" (
  SET AKIBAN_LOGCONF=%2
  SHIFT
  SHIFT
) ELSE IF "%1"=="-g" (
  SET JVM_OPTS=%JVM_OPTS% -Dcom.persistit.showgui=true
  SHIFT
) ELSE IF "%1"=="-m" (
  SET SERVICE_MODE=%2
  SHIFT
  SHIFT
) ELSE IF "%1"=="-su" (
  SET SERVICE_USER=%2
  SET SERVICE_PASSWORD=%3
  SHIFT
  SHIFT
  SHIFT
) ELSE (
  GOTO USAGE
)

GOTO NEXT_OPT
:END_OPT

IF NOT EXIST "%JAR_FILE%" (
  ECHO JAR file does not exist; try -j
  GOTO EOF
)
IF NOT EXIST "%DEP_DIR%" (
  ECHO server dependencies directory does not exist; try -d
  GOTO EOF
)

SET CLASSPATH=%JAR_FILE%;%DEP_DIR%\*

IF "%VERB%"=="version" GOTO VERSION

IF "%VERB%"=="start" (
  "%PRUNSRV%" //ES//%SERVICE_NAME%
  GOTO CHECK_ERROR
) ELSE IF "%VERB%"=="stop" (
  "%PRUNSRV%" //SS//%SERVICE_NAME%
  GOTO CHECK_ERROR
) ELSE IF "%VERB%"=="uninstall" (
  "%PRUNSRV%" //DS//%SERVICE_NAME%
  GOTO EOF
) ELSE IF "%VERB%"=="console" (
  "%PRUNSRV%" //TS//%SERVICE_NAME%
  GOTO EOF
) ELSE IF "%VERB%"=="monitor" (
  START "%SERVICE_DNAME%" "%PRUNMGR%" //MS//%SERVICE_NAME%
  GOTO EOF
)

IF NOT EXIST "%AKIBAN_CONF%\config\services-config.yaml" (
  ECHO Wrong configuration directory; try -c
  GOTO EOF
)

IF NOT DEFINED AKIBAN_LOGCONF SET AKIBAN_LOGCONF=%AKIBAN_CONF%\config\log4j.properties

IF EXIST "%AKIBAN_CONF%\config\jvm-options.cmd" CALL "%AKIBAN_CONF%\config\jvm-options.cmd"

IF "%VERB%"=="window" GOTO RUN_CMD
IF "%VERB%"=="run" GOTO RUN_CMD

SET PRUNSRV_ARGS=--StartMode=jvm --StartClass com.akiban.server.AkServer --StartMethod=procrunStart --StopMode=jvm --StopClass=com.akiban.server.AkServer --StopMethod=procrunStop --StdOutput="%AKIBAN_LOGDIR%\stdout.log" --DisplayName="%SERVICE_DNAME%" --Description="%SERVICE_DESC%" --Startup=%SERVICE_MODE% --Classpath="%CLASSPATH%"
REM Each value that might have a space needs a separate ++JvmOptions.
SET PRUNSRV_ARGS=%PRUNSRV_ARGS% --JvmOptions="%JVM_OPTS: =#%" ++JvmOptions="-Dakserver.config_dir=%AKIBAN_CONF%" ++JvmOptions="-Dservices.config=%AKIBAN_CONF%\config\services-config.yaml" ++JvmOptions="-Dlog4j.configuration=file:%AKIBAN_LOGCONF%"
IF DEFINED SERVICE_USER SET PRUNSRV_ARGS=%PRUNSRV_ARGS% --ServiceUser=%SERVICE_USER% --ServicePassword=%SERVICE_PASSWORD%
IF DEFINED MAX_HEAP_SIZE SET PRUNSRV_ARGS=%PRUNSRV_ARGS% --JvmMs=%MAX_HEAP_SIZE% --JvmMx=%MAX_HEAP_SIZE%

IF "%VERB%"=="install" (
  "%PRUNSRV%" //IS//%SERVICE_NAME% %PRUNSRV_ARGS%
  GOTO EOF
)

:USAGE
ECHO Usage: {install,uninstall,start,stop,run,window,monitor,version} [-j jarfile] [-c confdir] [-l log4j.properties] [-g] [-m manual,auto]
ECHO install   - install as service
ECHO uninstall - remove installed service
ECHO start     - start installed service
ECHO stop      - stop installed service
ECHO window    - run as ordinary application
ECHO run       - run in command window
ECHO monitor   - start tray icon service monitor
ECHO version   - print version and exit
GOTO EOF

:VERSION
FOR /F "usebackq" %%V IN (`java -cp "%CLASSPATH%" com.akiban.server.GetVersion`) DO SET SERVER_VERSION=%%V
FOR /F "usebackq" %%V IN (`java -cp "%CLASSPATH%" com.persistit.GetVersion`) DO SET PERSISTIT_VERSION=%%V
ECHO server   : %SERVER_VERSION%
ECHO persistit: %PERSISTIT_VERSION%
ECHO.
"%PRUNSRV%" //VS
GOTO EOF

:RUN_CMD
SET JVM_OPTS=%JVM_OPTS% -Dakserver.config_dir="%AKIBAN_CONF%"
SET JVM_OPTS=%JVM_OPTS% -Dservices.config="%AKIBAN_CONF%\config\services-config.yaml"
SET JVM_OPTS=%JVM_OPTS% -Dlog4j.configuration="file:%AKIBAN_LOGCONF%"
SET JVM_OPTS=%JVM_OPTS% -ea
SET JVM_OPTS=%JVM_OPTS% -Dakiban.home="%AKIBAN_HOME_DIR%"
IF DEFINED MAX_HEAP_SIZE SET JVM_OPTS=%JVM_OPTS% -Xms%MAX_HEAP_SIZE%-Xmx%MAX_HEAP_SIZE%
IF "%VERB%"=="window" GOTO WINDOW_CMD
java %JVM_OPTS% -cp "%CLASSPATH%" com.akiban.server.AkServer
GOTO EOF

:WINDOW_CMD
SET JVM_OPTS=%JVM_OPTS% "-Drequire:com.akiban.server.service.ui.SwingConsoleService" "-Dprioritize:com.akiban.server.service.ui.SwingConsoleService"
START javaw %JVM_OPTS% -cp "%CLASSPATH%" com.akiban.server.service.ui.AkServerWithSwingConsole
GOTO EOF

:CHECK_ERROR
IF ERRORLEVEL 1 GOTO PAUSE
GOTO EOF

:PAUSE
PAUSE
GOTO EOF

:EOF
ENDLOCAL
