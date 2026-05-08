# Team Alpha Planning Poker - Development Guide

## Prerequisites
- Java 21 (JDK)
- Gradle (provided via wrapper or use system gradle)

## Running Locally
1. Set your `JAVA_HOME` to a Java 21 JDK.
2. Run the application using Gradle:
   ```bash
   ./gradlew run
   ```
3. Open your browser at `http://localhost:8080`.

## Key Features
- **Real-Time:** Powered by Ktor WebSockets.
- **High Contrast:** Sparkassen-Rot and Black design.
- **Shortcuts:**
  - `1-9`: Select Fibonacci values.
  - `Space`: Reveal cards (Host only).
  - `Esc`: Reset round (Host only).

## Tech Stack
- **Backend:** Ktor (Kotlin)
- **Frontend:** Vanilla JS/CSS/HTML
- **Persistence:** None (In-Memory)
