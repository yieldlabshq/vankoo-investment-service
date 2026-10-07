# Etapa 1: Compilación
FROM maven:3.9.12-eclipse-temurin-25 AS build
WORKDIR /app

# 1. Copiamos el POM y descargamos dependencias (Caché de Docker)
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Copiamos el código fuente y generamos el JAR
COPY src ./src
RUN mvn clean package -DskipTests

# Etapa 2: Runtime (Imagen ligera)
FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

# Instalamos curl para que el healthcheck de Docker funcione
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*

# Usuario de seguridad para no correr como root
RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring

# Copiamos el JAR (ajustado al nombre del artifact generado por Maven en la carpeta target)
COPY --from=build /app/target/investment-*.jar app.jar

# Exponemos el puerto que configuramos en el YAML
EXPOSE 8082

# Ejecutamos con el perfil de docker activo
ENTRYPOINT ["java", "-Dspring.profiles.active=docker", "-jar", "app.jar"]
