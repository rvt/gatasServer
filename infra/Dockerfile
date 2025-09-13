# Build stage
FROM mingc/android-build-box:latest AS build
WORKDIR /workspace

COPY lib/ /workspace/lib
COPY app/ /workspace/app

# Build Library
WORKDIR /workspace/lib
RUN ./gradlew clean publishToMavenLocal -PCI=true

# Build the app
WORKDIR /workspace/app
RUN ./gradlew clean shadowDistZip
RUN unzip /workspace/app/build/distributions/gatasServer-shadow-*.zip -d /tmp/extract \
    && mkdir /app \
    && mv /tmp/extract/*/* /app/

# Build and use image
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app /app
CMD ["./bin/gatasServer"]
