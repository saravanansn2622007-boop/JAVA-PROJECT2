#!/usr/bin/env bash
echo "========================================================"
echo "Compiling KEC Evaluation System on OCI Linux..."
echo "========================================================"
javac -cp ".:lib/*" *.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi
echo "Running MainApp..."
java -cp ".:lib/*" MainApp

