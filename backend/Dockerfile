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

# Run the jar
ENTRYPOINT ["java", "-jar", "app.jar"]
