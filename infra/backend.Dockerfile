# syntax=docker/dockerfile:1

# --- Build stage: compila com Java 21 + Maven wrapper (host não tem Java 21) ---
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
# Cache de dependências: copia o pom primeiro
COPY pom.xml ./
COPY .mvn/ .mvn/
COPY mvnw ./
RUN ./mvnw -B -q dependency:go-offline
# Copia o código e empacota (pula testes no build de imagem; CI roda os testes)
COPY src/ src/
RUN ./mvnw -B -q clean package -DskipTests

# --- Runtime stage: JRE slim ---
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
# Tesseract OCR (Fase 3) + idiomas eng/por. Não redistribuímos tessdata do PDF24 (§11).
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng tesseract-ocr-por \
    && rm -rf /var/lib/apt/lists/*
# Usuário não-root
RUN useradd -r -u 1001 appuser
COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /tmp/pdfweb && chown -R appuser /tmp/pdfweb
USER appuser
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
