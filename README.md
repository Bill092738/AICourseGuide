# CourseGuide

A modern web app for course and career recommendations, featuring a Java Spring Boot backend and a React + TypeScript + Vite frontend.

## System Architecture

![System Architecture Diagram](images/system-architecture-diagram.png)

---

## Project Structure

```
CourseGuide/
  frontend/                # React + TypeScript + Vite frontend
    src/
      App.tsx
      main.tsx
      ...
    public/
    index.html
    package.json
    ...
  src/main/java/com/courseguide/
    App.java               # Spring Boot entrypoint
    ApiController.java     # Main API endpoints
    processors/            # Recommendation and data processors
    services/              # File storage and related services
    dto/                   # Data transfer objects (records, enums)
    utils/                 # Utility classes
    ...
  pom.xml                  # Maven build file
  ...
```

---

## Prerequisites

- Java 21+ (backend uses Java 21 features)
- Node.js 18+ and npm (frontend development)
- Maven 3.6+ (backend build)
- MySQL 8.0+ (course database and prerequisite DAG)
- Llama API Server running on http://localhost:8075 (LLM analysis)
- Optional
- Playwright (auto-installed by Maven for web scraping)

---

## Backend: How to Build & Run

1. **Compile the backend:**
   ```sh
   cd CourseGuide
   mvn clean package
   ```

2. **Run the backend:**
   ```sh
   java -jar target/courseguide-0.1.0-SNAPSHOT.jar
   ```

   The backend will start at [http://localhost:8080](http://localhost:8080).

---

## Frontend: How to Develop

1. **Install dependencies:**
   ```sh
   cd CourseGuide/frontend
   npm install
   ```

2. **Start the development server:**
   ```sh
   npm run dev
   ```
   The frontend will be available at [http://localhost:5173](http://localhost:5173) and will proxy API requests to the backend.

3. **Build for production:**
   ```sh
   npm run build
   ```

---

## Usage

- Open [http://localhost:5173](http://localhost:5173) for the modern React frontend.

---

## API Endpoints

- `POST /api/recommendations` — Simple recommendations (JSON: `{ major, gpa }`)
- `POST /api/upload-progress` — Upload a progress PDF (multipart/form-data)
- `POST /api/recommendations/profile` — Rich recommendations (JSON profile, can reference uploaded PDF)
- `GET /api/health` — Health check endpoint

---

## Linting & Formatting

- Frontend uses ESLint (see [`frontend/eslint.config.js`](CourseGuide/frontend/eslint.config.js))
- Prettier or other formatting tools can be added as needed.

---