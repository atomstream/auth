FROM docker.io/library/clojure:temurin-25-tools-deps AS builder

WORKDIR /app

COPY deps.edn ./
COPY src ./src/
COPY resources ./resources/
COPY bin ./bin/

RUN ./bin/build uberjar

#-----------------------------------------------#

FROM docker.io/library/eclipse-temurin:25-jre-alpine AS runtime

COPY --from=builder /app/target/auth-uberjar.jar /
COPY container/atomadmin /usr/bin/atomadmin

EXPOSE 4800

ENV OAK__ENV=prod

ENTRYPOINT ["java", "-jar", "/auth-uberjar.jar"]
CMD ["run"]
