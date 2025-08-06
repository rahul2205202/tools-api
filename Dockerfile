FROM gcr.io/google-appengine/openjdk17

# Install liblcms2
RUN apt-get update && apt-get install -y liblcms2-2 && apt-get clean

# Add your app
ADD . /app
WORKDIR /app

CMD ["java", "-jar", "my-tools-io-0.0.1.jar"]
