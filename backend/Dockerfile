FROM eclipse-temurin:25.0.4.1_1-jdk-alpine@sha256:5122b1df12f028f10c7399bedf1212e1497962a6482af3338eea2ace485f561e AS build
ARG SOURCE_DATE_EPOCH
ARG CINENEST_BUILD_ID
WORKDIR /source/backend
COPY backend/mvnw ./
COPY backend/.mvn .mvn
COPY backend/pom.xml ./
COPY backend/src/main src/main
RUN test -n "$SOURCE_DATE_EPOCH" && test -n "$CINENEST_BUILD_ID" && ./mvnw -B -C -ntp -Dmaven.test.skip=true -Dproject.build.outputTimestamp="$SOURCE_DATE_EPOCH" package && cp target/cinenest-0.0.1-SNAPSHOT.jar /application.jar
FROM eclipse-temurin:25.0.4.1_1-jre-alpine@sha256:b7528962e65d43064d8a8b67ba175367896bc948363ea2d3fcd7f10d34e1f048
ARG CINENEST_BUILD_ID
LABEL com.cinenest.packaging.snapshot=$CINENEST_BUILD_ID
WORKDIR /app
COPY --from=build --chown=10001:10001 /application.jar /app/application.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=cloud-run
ENTRYPOINT ["java","-jar","/app/application.jar"]
