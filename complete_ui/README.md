# EV Buddy JavaFX UI

## How to Run

1. Make sure you have Java 11+ and JavaFX SDK installed.
2. Place your logo image as `logo.png` in `src/main/resources/` (or use the placeholder).
3. Compile and run the app:
   - From the `complete_ui` directory:
     ```sh
     javac -d out --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml src/main/java/*.java
     java --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml -cp out main.java.Main
     ```
   - Adjust the JavaFX SDK path as needed for your system.

## Project Structure
- `src/main/java/` — Java source files
- `src/main/resources/` — FXML, CSS, and images

## Features
- UI matches the provided Figma design
- "AD" badge disappears on click
- Easy to tweak styles in `evbuddy.css` 