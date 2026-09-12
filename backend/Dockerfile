# 上海如静知华信息科技有限公司 https://www.zhuatech.cn/
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,id=zhuatech-maven-build,target=/root/.m2 mvn -q -DskipTests package
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -g 10001 app && adduser -D -u 10001 -G app app
WORKDIR /app
COPY --from=build --chown=app:app /app/target/app.jar ./app.jar
USER app
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-Duser.timezone=UTC","-jar","/app/app.jar"]
