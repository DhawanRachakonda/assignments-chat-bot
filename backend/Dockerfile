# ---- build (JDK 27 + Maven wrapper) ----
FROM eclipse-temurin:27-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src ./src
RUN ./mvnw -q -B -DskipTests package

# ---- run ----
FROM eclipse-temurin:27-jre
WORKDIR /app
COPY --from=build /app/target/assignments-chatbot-*.jar app.jar
# Small-heap tuning for Render's 512 MB plan: serial GC, C1-only JIT, small thread stacks.
ENV JAVA_TOOL_OPTIONS="-XX:+UseSerialGC -XX:MaxRAMPercentage=60 -XX:TieredStopAtLevel=1 -Xss512k"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
