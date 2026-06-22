#!/usr/bin/env bash
set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$PROJECT_DIR/CourseGuide"
FRONTEND_DIR="$PROJECT_DIR/CourseGuide/frontend"
BACKEND_PORT=8080
FRONTEND_PORT=5173

# Colors
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC} $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC} $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*"; exit 1; }

cleanup() {
  info "Shutting down..."
  [ -n "$BACKEND_PID" ] && kill "$BACKEND_PID" 2>/dev/null
  [ -n "$FRONTEND_PID" ] && kill "$FRONTEND_PID" 2>/dev/null
  wait 2>/dev/null
  info "All services stopped."
}
trap cleanup EXIT INT TERM

# --- Check prerequisites ---
command -v java   >/dev/null 2>&1 || error "Java not found. Install Java 21+."
command -v mvn    >/dev/null 2>&1 || error "Maven not found. Install Maven 3.6+."
command -v node   >/dev/null 2>&1 || error "Node.js not found. Install Node.js 18+."
command -v npm    >/dev/null 2>&1 || error "npm not found."

info "All prerequisites found."

# --- Build backend ---
info "Building backend..."
cd "$BACKEND_DIR"
mvn clean package -q -DskipTests
info "Backend built successfully."

# --- Start backend ---
info "Starting backend on port $BACKEND_PORT..."
java -jar "$BACKEND_DIR/target/"*.jar &
BACKEND_PID=$!

# Wait for backend to be ready
info "Waiting for backend to start..."
for i in $(seq 1 30); do
  if curl -s "http://localhost:$BACKEND_PORT/api/health" >/dev/null 2>&1; then
    info "Backend is ready."
    break
  fi
  if [ "$i" -eq 30 ]; then
    error "Backend failed to start within 30 seconds."
  fi
  sleep 1
done

# --- Install frontend dependencies ---
cd "$FRONTEND_DIR"
if [ ! -d "node_modules" ]; then
  info "Installing frontend dependencies..."
  npm install --silent
fi

# --- Start frontend ---
info "Starting frontend on port $FRONTEND_PORT..."
npm run dev &
FRONTEND_PID=$!

sleep 2

echo ""
echo "==========================================="
info "CourseGuide is running!"
echo "==========================================="
echo ""
echo "  Frontend:  http://localhost:$FRONTEND_PORT"
echo "  Backend:   http://localhost:$BACKEND_PORT"
echo "  Health:    http://localhost:$BACKEND_PORT/api/health"
echo ""
echo "  Press Ctrl+C to stop all services."
echo ""

# Wait for either process to exit
wait
