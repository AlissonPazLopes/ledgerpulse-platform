FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

RUN apk add --no-cache maven

COPY pom.xml ./
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S ledgerpulse && adduser -S ledgerpulse -G ledgerpulse
COPY --from=builder /build/target/*.jar /app/app.jar
RUN chown ledgerpulse:ledgerpulse /app/app.jar

USER ledgerpulse
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
