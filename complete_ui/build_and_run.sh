#!/bin/bash

# Set your JavaFX SDK path here
PATH_TO_FX="$HOME/Downloads/javafx-sdk-24.0.1/lib"

# Build the JavaFX application with JSON library
echo "Building EVBuddy Charger UI..."

# Create lib directory if it doesn't exist
mkdir -p lib

# Download JSON library if not present
if [ ! -f "lib/json-20231013.jar" ]; then
    echo "Downloading JSON library..."
    curl -L -o lib/json-20231013.jar https://repo1.maven.org/maven2/org/json/json/20231013/json-20231013.jar
fi

# Safely copy all resources (including images) to output directory
cp -a src/main/resources/. out/

# Compile Java files
javac --module-path "$PATH_TO_FX" --add-modules javafx.controls,javafx.fxml,javafx.media -cp "lib/*" -d out src/main/java/*.java

# Run the application
echo "Starting EVBuddy Charger UI..."
java --module-path "$PATH_TO_FX" --add-modules javafx.controls,javafx.fxml,javafx.media -cp "out:lib/*" main.java.Main 