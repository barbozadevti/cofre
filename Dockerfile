# Etapa 1: compila com Maven (dependências em camada própria, para aproveitar o cache).
FROM maven:3.9-eclipse-temurin-21 AS compilacao
WORKDIR /fonte
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests

# Etapa 2: só o JRE e o jar, rodando com usuário sem privilégios.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 cofre
WORKDIR /app
COPY --from=compilacao /fonte/target/cofre.jar cofre.jar
USER cofre
EXPOSE 5230
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "cofre.jar"]
