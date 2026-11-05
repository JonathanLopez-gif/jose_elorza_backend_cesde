# ============================================
# Etapa 1: Build (compila el proyecto con Maven)
# ============================================
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /app

# Copiamos primero el pom.xml para aprovechar la caché de Docker
# (si no cambian las dependencias, no se vuelven a descargar)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Ahora copiamos el código fuente
COPY src ./src

# Compilamos y empaquetamos (saltamos tests para build más rápido)
RUN mvn clean package -DskipTests

# ============================================
# Etapa 2: Runtime (imagen final, liviana)
# ============================================
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Usuario sin privilegios: la app no corre como root dentro del contenedor
RUN addgroup -S spring && adduser -S spring -G spring

# Copiamos solo el .jar generado desde la etapa de build
COPY --from=build --chown=spring:spring /app/target/app.jar app.jar

USER spring

# Puerto que expone la app (ajusta si usas otro en application.yml)
EXPOSE 8080

# Comando para ejecutar la app
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]