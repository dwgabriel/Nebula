@REM ----------------------------------------------------------------------------
@REM  Copyright 2001-2006 The Apache Software Foundation.
@REM
@REM  Licensed under the Apache License, Version 2.0 (the "License");
@REM  you may not use this file except in compliance with the License.
@REM  You may obtain a copy of the License at
@REM
@REM       http://www.apache.org/licenses/LICENSE-2.0
@REM
@REM  Unless required by applicable law or agreed to in writing, software
@REM  distributed under the License is distributed on an "AS IS" BASIS,
@REM  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@REM  See the License for the specific language governing permissions and
@REM  limitations under the License.
@REM ----------------------------------------------------------------------------
@REM
@REM   Copyright (c) 2001-2006 The Apache Software Foundation.  All rights
@REM   reserved.

@echo off

set ERROR_CODE=0

:init
@REM Decide how to startup depending on the version of windows

@REM -- Win98ME
if NOT "%OS%"=="Windows_NT" goto Win9xArg

@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" @setlocal

@REM -- 4NT shell
if "%eval[2+2]" == "4" goto 4NTArgs

@REM -- Regular WinNT shell
set CMD_LINE_ARGS=%*
goto WinNTGetScriptDir

@REM The 4NT Shell from jp software
:4NTArgs
set CMD_LINE_ARGS=%$
goto WinNTGetScriptDir

:Win9xArg
@REM Slurp the command line arguments.  This loop allows for an unlimited number
@REM of arguments (up to the command line limit, anyway).
set CMD_LINE_ARGS=
:Win9xApp
if %1a==a goto Win9xGetScriptDir
set CMD_LINE_ARGS=%CMD_LINE_ARGS% %1
shift
goto Win9xApp

:Win9xGetScriptDir
set SAVEDIR=%CD%
%0\
cd %0\..\.. 
set BASEDIR=%CD%
cd %SAVEDIR%
set SAVE_DIR=
goto repoSetup

:WinNTGetScriptDir
for %%i in ("%~dp0..") do set "BASEDIR=%%~fi"

:repoSetup
set REPO=


if "%JAVACMD%"=="" set JAVACMD=java

if "%REPO%"=="" set REPO=%BASEDIR%\repo

set CLASSPATH="%BASEDIR%"\etc;"%REPO%"\org\apache\tomcat\embed\tomcat-embed-core\8.5.38\tomcat-embed-core-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-annotations-api\8.5.38\tomcat-annotations-api-8.5.38.jar;"%REPO%"\org\apache\tomcat\embed\tomcat-embed-jasper\8.5.38\tomcat-embed-jasper-8.5.38.jar;"%REPO%"\org\apache\tomcat\embed\tomcat-embed-el\8.5.38\tomcat-embed-el-8.5.38.jar;"%REPO%"\org\eclipse\jdt\ecj\3.12.3\ecj-3.12.3.jar;"%REPO%"\org\apache\tomcat\tomcat-jasper\8.5.38\tomcat-jasper-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-servlet-api\8.5.38\tomcat-servlet-api-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-juli\8.5.38\tomcat-juli-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-el-api\8.5.38\tomcat-el-api-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-api\8.5.38\tomcat-api-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-util-scan\8.5.38\tomcat-util-scan-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-util\8.5.38\tomcat-util-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-jasper-el\8.5.38\tomcat-jasper-el-8.5.38.jar;"%REPO%"\org\apache\tomcat\tomcat-jsp-api\8.5.38\tomcat-jsp-api-8.5.38.jar;"%REPO%"\com\spotify\docker-client\8.14.1\docker-client-8.14.1-shaded.jar;"%REPO%"\com\google\guava\guava\20.0\guava-20.0.jar;"%REPO%"\com\fasterxml\jackson\jaxrs\jackson-jaxrs-json-provider\2.9.6\jackson-jaxrs-json-provider-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\jaxrs\jackson-jaxrs-base\2.9.6\jackson-jaxrs-base-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\module\jackson-module-jaxb-annotations\2.9.6\jackson-module-jaxb-annotations-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\datatype\jackson-datatype-guava\2.9.6\jackson-datatype-guava-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-core\2.9.6\jackson-core-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-databind\2.9.6\jackson-databind-2.9.6.jar;"%REPO%"\com\fasterxml\jackson\core\jackson-annotations\2.9.0\jackson-annotations-2.9.0.jar;"%REPO%"\org\glassfish\jersey\core\jersey-client\2.22.2\jersey-client-2.22.2.jar;"%REPO%"\javax\ws\rs\javax.ws.rs-api\2.0.1\javax.ws.rs-api-2.0.1.jar;"%REPO%"\org\glassfish\jersey\core\jersey-common\2.22.2\jersey-common-2.22.2.jar;"%REPO%"\javax\annotation\javax.annotation-api\1.2\javax.annotation-api-1.2.jar;"%REPO%"\org\glassfish\jersey\bundles\repackaged\jersey-guava\2.22.2\jersey-guava-2.22.2.jar;"%REPO%"\org\glassfish\hk2\osgi-resource-locator\1.0.1\osgi-resource-locator-1.0.1.jar;"%REPO%"\org\glassfish\hk2\hk2-api\2.4.0-b34\hk2-api-2.4.0-b34.jar;"%REPO%"\org\glassfish\hk2\hk2-utils\2.4.0-b34\hk2-utils-2.4.0-b34.jar;"%REPO%"\org\glassfish\hk2\external\aopalliance-repackaged\2.4.0-b34\aopalliance-repackaged-2.4.0-b34.jar;"%REPO%"\org\glassfish\hk2\external\javax.inject\2.4.0-b34\javax.inject-2.4.0-b34.jar;"%REPO%"\org\glassfish\hk2\hk2-locator\2.4.0-b34\hk2-locator-2.4.0-b34.jar;"%REPO%"\org\javassist\javassist\3.18.1-GA\javassist-3.18.1-GA.jar;"%REPO%"\org\glassfish\jersey\connectors\jersey-apache-connector\2.22.2\jersey-apache-connector-2.22.2.jar;"%REPO%"\org\glassfish\jersey\media\jersey-media-json-jackson\2.22.2\jersey-media-json-jackson-2.22.2.jar;"%REPO%"\org\glassfish\jersey\ext\jersey-entity-filtering\2.22.2\jersey-entity-filtering-2.22.2.jar;"%REPO%"\javax\activation\activation\1.1\activation-1.1.jar;"%REPO%"\org\apache\commons\commons-compress\1.9\commons-compress-1.9.jar;"%REPO%"\commons-io\commons-io\2.5\commons-io-2.5.jar;"%REPO%"\org\apache\httpcomponents\httpclient\4.5\httpclient-4.5.jar;"%REPO%"\commons-logging\commons-logging\1.2\commons-logging-1.2.jar;"%REPO%"\commons-codec\commons-codec\1.9\commons-codec-1.9.jar;"%REPO%"\org\apache\httpcomponents\httpcore\4.4.5\httpcore-4.4.5.jar;"%REPO%"\com\github\jnr\jnr-unixsocket\0.18\jnr-unixsocket-0.18.jar;"%REPO%"\com\github\jnr\jnr-ffi\2.1.4\jnr-ffi-2.1.4.jar;"%REPO%"\com\github\jnr\jffi\1.2.15\jffi-1.2.15.jar;"%REPO%"\com\github\jnr\jffi\1.2.15\jffi-1.2.15-native.jar;"%REPO%"\org\ow2\asm\asm\5.0.3\asm-5.0.3.jar;"%REPO%"\org\ow2\asm\asm-commons\5.0.3\asm-commons-5.0.3.jar;"%REPO%"\org\ow2\asm\asm-analysis\5.0.3\asm-analysis-5.0.3.jar;"%REPO%"\org\ow2\asm\asm-tree\5.0.3\asm-tree-5.0.3.jar;"%REPO%"\org\ow2\asm\asm-util\5.0.3\asm-util-5.0.3.jar;"%REPO%"\com\github\jnr\jnr-x86asm\1.0.2\jnr-x86asm-1.0.2.jar;"%REPO%"\com\github\jnr\jnr-constants\0.9.8\jnr-constants-0.9.8.jar;"%REPO%"\com\github\jnr\jnr-enxio\0.16\jnr-enxio-0.16.jar;"%REPO%"\com\github\jnr\jnr-posix\3.0.35\jnr-posix-3.0.35.jar;"%REPO%"\commons-lang\commons-lang\2.6\commons-lang-2.6.jar;"%REPO%"\org\bouncycastle\bcpkix-jdk15on\1.59\bcpkix-jdk15on-1.59.jar;"%REPO%"\org\bouncycastle\bcprov-jdk15on\1.59\bcprov-jdk15on-1.59.jar;"%REPO%"\org\slf4j\slf4j-api\1.7.22\slf4j-api-1.7.22.jar;"%REPO%"\com\google\apis\google-api-services-drive\v3-rev173-1.25.0\google-api-services-drive-v3-rev173-1.25.0.jar;"%REPO%"\com\google\api-client\google-api-client\1.25.0\google-api-client-1.25.0.jar;"%REPO%"\com\google\oauth-client\google-oauth-client\1.25.0\google-oauth-client-1.25.0.jar;"%REPO%"\com\google\http-client\google-http-client\1.25.0\google-http-client-1.25.0.jar;"%REPO%"\com\google\j2objc\j2objc-annotations\1.1\j2objc-annotations-1.1.jar;"%REPO%"\com\google\code\findbugs\jsr305\3.0.2\jsr305-3.0.2.jar;"%REPO%"\com\google\http-client\google-http-client-jackson2\1.25.0\google-http-client-jackson2-1.25.0.jar;"%REPO%"\nebula\nebula-server\1.0-SNAPSHOT\nebula-server-1.0-SNAPSHOT.jar

set ENDORSED_DIR=
if NOT "%ENDORSED_DIR%" == "" set CLASSPATH="%BASEDIR%"\%ENDORSED_DIR%\*;%CLASSPATH%

if NOT "%CLASSPATH_PREFIX%" == "" set CLASSPATH=%CLASSPATH_PREFIX%;%CLASSPATH%

@REM Reaching here means variables are defined and arguments have been captured
:endInit

%JAVACMD% %JAVA_OPTS%  -classpath %CLASSPATH% -Dapp.name="nebulaserver" -Dapp.repo="%REPO%" -Dapp.home="%BASEDIR%" -Dbasedir="%BASEDIR%" nebula.nebulaserver.Server %CMD_LINE_ARGS%
if %ERRORLEVEL% NEQ 0 goto error
goto end

:error
if "%OS%"=="Windows_NT" @endlocal
set ERROR_CODE=%ERRORLEVEL%

:end
@REM set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" goto endNT

@REM For old DOS remove the set variables from ENV - we assume they were not set
@REM before we started - at least we don't leave any baggage around
set CMD_LINE_ARGS=
goto postExec

:endNT
@REM If error code is set to 1 then the endlocal was done already in :error.
if %ERROR_CODE% EQU 0 @endlocal


:postExec

if "%FORCE_EXIT_ON_ERROR%" == "on" (
  if %ERROR_CODE% NEQ 0 exit %ERROR_CODE%
)

exit /B %ERROR_CODE%
