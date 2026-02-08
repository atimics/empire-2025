## Stage 1: Build the Rust WASM client
FROM rust:1-bookworm AS wasm-builder

RUN cargo install wasm-pack --locked

WORKDIR /build
COPY client/ client/
RUN cd client && wasm-pack build --target web --out-dir ../pkg

## Stage 2: Run the Clojure game server
FROM eclipse-temurin:21-jdk-jammy

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    rlwrap \
    && rm -rf /var/lib/apt/lists/*

# Install Clojure CLI
RUN curl -L https://download.clojure.org/install/linux-install-1.12.0.1530.sh | bash

WORKDIR /app

# Copy deps first to cache dependency resolution
COPY deps.edn .
RUN clojure -P -M:server

# Copy the rest of the project
COPY src/ src/
COPY spec/ spec/
COPY acceptanceTests/ acceptanceTests/
COPY resources/ resources/

# Copy built WASM assets from builder stage
COPY --from=wasm-builder /build/pkg/ resources/public/pkg/

EXPOSE 8080

COPY docker-start.sh /docker-start.sh
RUN chmod +x /docker-start.sh

CMD ["/docker-start.sh"]
