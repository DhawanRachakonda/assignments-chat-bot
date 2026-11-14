# Native image build (GraalVM 25). Needs several GB of RAM while compiling; the runtime image is tiny.

# ---- build ----
FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -Pnative -DskipTests native:compile

# ---- run ----
FROM debian:bookworm-slim
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/assignments-chatbot app
EXPOSE 8080
# Native images size their heap at runtime; cap it for Render's 512 MB plan.
ENTRYPOINT ["./app", "-Xmx256m"]
