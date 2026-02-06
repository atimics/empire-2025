FROM eclipse-temurin:21-jdk-jammy

# Install Xvfb, x11vnc, noVNC, and OpenGL software rendering
RUN apt-get update && apt-get install -y --no-install-recommends \
    xvfb \
    x11vnc \
    novnc \
    websockify \
    libgl1-mesa-dri \
    libgl1-mesa-glx \
    libegl1-mesa \
    libxrender1 \
    libxtst6 \
    libxi6 \
    libxrandr2 \
    libxcursor1 \
    libxcomposite1 \
    libxdamage1 \
    libxfixes3 \
    libfontconfig1 \
    fonts-dejavu \
    curl \
    rlwrap \
    && rm -rf /var/lib/apt/lists/*

# Install Clojure CLI
RUN curl -L https://download.clojure.org/install/linux-install-1.12.0.1530.sh | bash

WORKDIR /app

# Copy deps first to cache dependency resolution
COPY deps.edn .
RUN clojure -P

# Copy the rest of the project
COPY src/ src/
COPY spec/ spec/

# Expose noVNC web port
EXPOSE 6080

# Startup script
COPY docker-start.sh /docker-start.sh
RUN chmod +x /docker-start.sh

CMD ["/docker-start.sh"]
