#!/bin/bash

# Test build script to verify everything works before Docker build
# Usage: ./test-build.sh

set -e

echo "🧪 Testing local build process..."

# Load environment variables
if [ -f .env ]; then
    echo "Loading environment variables from .env..."
    set -a
    source .env
    set +a
fi

# Check if required tools are available
echo "Checking build tools..."
if ! command -v npm &> /dev/null; then
    echo "❌ npm is not installed"
    exit 1
fi

if ! command -v node &> /dev/null; then
    echo "❌ node is not installed"
    exit 1
fi

echo "✅ Node version: $(node --version)"
echo "✅ NPM version: $(npm --version)"

# Check if dependencies are installed
if [ ! -d "node_modules" ]; then
    echo "Installing dependencies..."
    npm install
fi

# Check if vite is available
if ! npx vite --version &> /dev/null; then
    echo "❌ Vite is not available. Installing dependencies..."
    npm install
fi

echo "✅ Vite version: $(npx vite --version)"

# Test the build
echo "🔨 Testing build process..."
npm run build

if [ -d "dist" ]; then
    echo "✅ Build successful! Output directory 'dist' created."
    echo "📁 Build contents:"
    ls -la dist/
    
    # Check if index.html exists
    if [ -f "dist/index.html" ]; then
        echo "✅ index.html found in build output"
    else
        echo "❌ index.html not found in build output"
        exit 1
    fi
    
    # Check build size
    BUILD_SIZE=$(du -sh dist/ | cut -f1)
    echo "📦 Build size: $BUILD_SIZE"
    
else
    echo "❌ Build failed - no dist directory created"
    exit 1
fi

echo ""
echo "🎉 Local build test completed successfully!"
echo "You can now proceed with Docker build using: ./build-docker.sh"