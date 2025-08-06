FROM eclipse-temurin:17-jdk

# Install liblcms2 and clean up
RUN apt-get update \
    && apt-get install -y liblcms2-2 \
    && rm -rf /var/lib/apt/lists/*

# Copy your app
WORKDIR /app
COPY target/my-tools-io-0.0.1.jar my-tools-io-0.0.1.jar

# Run the app
CMD ["java", "-jar", "my-tools-io-0.0.1.jar"]