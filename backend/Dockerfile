# Stage 1: Build the application
FROM maven:3.8.7-eclipse-temurin-17 AS build
WORKDIR /app

# Step 1: Copy ONLY the pom.xml first
COPY pom.xml .

# Step 2: Download dependencies (This layer will be cached by Docker/Render)
RUN mvn dependency:go-offline -B

# Step 3: Copy the actual source code
COPY src ./src

# Step 4: Package the application
RUN mvn clean package -DskipTests

# Stage 2: Run the application (Using JRE for a smaller, more secure image)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/wealthwise-backend-0.0.1-SNAPSHOT.jar app.jar

# Expose the port the app runs on
EXPOSE 8080

# Run the jar — activates the 'prod' Spring profile so application-prod.properties
# is loaded and all secrets are read from Render's environment variables
# JVM flags tuned for Render free tier (512 MB RAM, 0.1 vCPU, frequent cold starts)
# -XX:TieredStopAtLevel=1  → skip JIT C2 compilation — faster startup (< 20s vs 40s+)
# -Xss512k                 → reduce per-thread stack (saves ~2 MB per connection)
# -XX:MaxRAMPercentage=70  → cap heap at 70% of container limit to avoid OOM kills
# -XX:+UseSerialGC        → serial GC uses less memory than G1, good for low-traffic
# -Dfile.encoding=UTF-8   → explicit encoding to prevent locale-based issues on Linux
ENTRYPOINT ["java", \
  "-XX:TieredStopAtLevel=1", \
  "-XX:MaxRAMPercentage=70.0", \
  "-XX:+UseSerialGC", \
  "-Xss512k", \
  "-Dfile.encoding=UTF-8", \
  "-Dspring.profiles.active=prod", \
  "-jar", "app.jar"]
