# Build stage
FROM maven:3.6.3-jdk-11 as build

COPY ./ /home/dpanalyzer
WORKDIR /home/dpanalyzer
RUN mvn clean package -Dmaven.test.skip=true


# Package stage
FROM openjdk:11.0.4-jre-slim

COPY --from=build /home/dpanalyzer/target/dpanalyzer-fat.jar /opt/verticles/dpanalyzer.jar
COPY --from=build /home/dpanalyzer/src/config/docker.json /opt/verticles/
COPY --from=build /home/dpanalyzer/ds /opt/verticles/ds

EXPOSE 7070

WORKDIR /opt/verticles

ENTRYPOINT ["sh", "-c"]
CMD ["java -Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory -jar dpanalyzer.jar -cluster -ha -conf docker.json"]