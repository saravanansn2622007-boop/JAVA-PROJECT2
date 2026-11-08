@echo off
echo ========================================================
echo Compiling KEC Evaluation System...
echo ========================================================
javac -cp ".;lib/*" *.java
if %errorlevel% neq 0 (
    echo Compilation failed!
    pause
    exit /b %errorlevel%
)
echo Running MainApp...
java -cp ".;lib/*" MainApp
pause

