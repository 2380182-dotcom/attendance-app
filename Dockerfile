# ==========================================
# BUILD STAGE
# ==========================================
FROM maven:3.9.6-eclipse-temurin-21-alpine AS build
WORKDIR /app

# Copy POM and download dependencies to optimize caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy code and build JAR
COPY src ./src
RUN mvn clean package -DskipTests -B

# ==========================================
# RUN STAGE
# ==========================================
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy build artifact
COPY --from=build /app/target/attendance-0.0.1-SNAPSHOT.jar app.jar

# Render injects PORT env variable, application binds to it
EXPOSE 8080

# Memory budget for Render Starter (512 MB for the WHOLE container). Measured
# with Native Memory Tracking: ~200 MB lives outside the heap (class metadata
# ~82 MB, symbols ~33, compiled code ~29, classes ~15, compiler/arenas ~15,
# threads). Java's default heap (25% = 128 MB) was too small, and a bare
# -Xmx512m would overflow the container. So:
#   heap 45% (~230 MB) + capped metaspace / code cache / direct memory,
#   small thread stacks, SerialGC (least overhead on a small container),
#   and exit on OutOfMemoryError so Render restarts a clean process instead
#   of leaving a half-dead one answering 502s.
# Heap that is not in use is handed back (measured 2026-10-28 on the
# production backup: only ~60 MB of the heap is live after a full GC, but
# SerialGC kept 180-206 MB committed, decided at random during startup, so
# the container idled anywhere from 398 to 478 MB). A 10% initial heap and
# Min/MaxHeapFreeRatio 15/40 let the heap shrink after each full GC; the
# 45% ceiling is unchanged, so spikes still have the same room.
# JAVA_TOOL_OPTIONS is read by the JVM even if Render's "Docker Command"
# overrides the ENTRYPOINT below.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=45 -XX:InitialRAMPercentage=10 -XX:MinHeapFreeRatio=15 -XX:MaxHeapFreeRatio=40 -XX:MaxMetaspaceSize=150m -XX:ReservedCodeCacheSize=48m -XX:MaxDirectMemorySize=32m -Xss512k -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError"

# Run Spring Boot app
ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
