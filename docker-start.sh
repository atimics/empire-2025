#!/bin/bash
set +e

# Start Xvfb (virtual framebuffer) on display :99
Xvfb :99 -screen 0 1280x1080x24 &
sleep 1

export DISPLAY=:99
export LIBGL_ALWAYS_SOFTWARE=1

# Start x11vnc on the virtual display
x11vnc -display :99 -forever -nopw -shared -rfbport 5900 &
sleep 1

# Start noVNC (serves VNC via websocket to browser)
websockify --web /usr/share/novnc/ 6080 localhost:5900 &
sleep 1

echo "============================================"
echo "  Open http://localhost:6080/vnc.html"
echo "  then click Connect"
echo "============================================"

# Run the game — keep stdout/stderr visible
cd /app
clojure -M:run 2>&1
echo "Game exited with code $?"
# Keep container alive so we can inspect
sleep infinity
