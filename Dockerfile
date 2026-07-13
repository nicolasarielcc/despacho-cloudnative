# ============================================================
# DOCKERFILE — Sistema de Gestión de Guías de Despacho
# Asignatura: CDY2204 - Desarrollo Cloud Native
# Sumativa 3
#
# Este Dockerfile construye la imagen del microservicio en 2 etapas:
#   1. Etapa de BUILD:   compila el .jar con Maven
#   2. Etapa de RUNTIME: ejecuta el .jar con una JRE ligera
#
# IMPORTANTE:
# - Si usas wallet de Oracle Cloud, copia la carpeta wallet/
#   a src/main/resources/wallet/ antes de ejecutar docker build.
# - Descomenta la línea COPY del wallet si es necesario.
# ============================================================

# --- ETAPA 1: BUILD (compilación con Maven) ---
FROM maven:3.9-eclipse-temurin-17-alpine AS build

# Directorio de trabajo dentro del contenedor
WORKDIR /app

# Copiar archivos de configuración de Maven primero (cachea dependencias)
COPY pom.xml .

# Descargar dependencias (esta capa se cachea si pom.xml no cambia)
# Si usas Oracle JDBC, necesitas colocarlo en ./lib/ y ajustar el repositorio
RUN mvn dependency:go-offline -B || echo "Algunas dependencias no se pudieron resolver offline"

# Copiar el código fuente
COPY src ./src

# Compilar y empaquetar (sin ejecutar tests en esta etapa)
RUN mvn clean package -Dmaven.test.skip=true -B

# --- ETAPA 2: RUNTIME (solo el .jar en una imagen ligera) ---
FROM eclipse-temurin:17-jre-alpine AS runtime

# Directorio de trabajo
WORKDIR /app

# Copiar el .jar compilado desde la etapa de build
COPY --from=build /app/target/*.jar app.jar

# (Opcional) Copiar wallet de Oracle Cloud si se usa
# COPY wallet/ /app/wallet/

# Puerto que expone la aplicación
EXPOSE 8080

# Punto de entrada: ejecutar el .jar
ENTRYPOINT ["java", "-jar", "app.jar"]
