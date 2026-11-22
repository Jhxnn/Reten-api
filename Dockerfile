# Imagem da API do Resten. Usada no Render, mas roda em qualquer lugar com Docker.
#   docker build -t resten-api .
#   docker run -p 8090:8090 --env-file .env resten-api

# --- Build: compila e empacota o jar (sem rodar os testes, que precisam de Docker) ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Baixa as dependências numa camada separada: só refaz quando o pom muda.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

COPY src src
RUN ./mvnw -q -B package -DskipTests && cp target/*.jar app.jar

# --- Execução: só o JRE e o jar ---
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN useradd --system --uid 1001 resten
USER resten

COPY --from=build /app/app.jar app.jar

# Ajustado para instâncias pequenas (512 MB, pouca CPU, como o plano grátis do Render):
# heap limitado a 70% da memória do contêiner, GC simples e JIT só no primeiro nível,
# o que encurta bastante o tempo de subida.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k"

# O Render informa a porta em PORT; fora dele vale RETEN_SERVER_PORT (8090).
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
