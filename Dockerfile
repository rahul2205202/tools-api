FROM eclipse-temurin:17-jdk

# Install liblcms2
RUN apt-get update && apt-get install -y liblcms2-2 && apt-get clean

# Copy your app
WORKDIR /app
COPY target/my-tools-io-0.0.1.jar /app/app.jar

CMD ["java", "-jar", "my-tools-io-0.0.1.jar"]
