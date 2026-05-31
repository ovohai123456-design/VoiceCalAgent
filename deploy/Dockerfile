FROM eclipse-temurin:8-jre-jammy

WORKDIR /app

RUN groupadd --system voicecal \
    && useradd --system --gid voicecal --home-dir /app voicecal

COPY --chown=voicecal:voicecal app.jar /app/app.jar

USER voicecal

ENV TZ=Asia/Shanghai

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
