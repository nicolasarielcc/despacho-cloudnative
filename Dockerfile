# =========================================================================
# ETAPA 1: Compilación y empaquetado del código fuente (Build Stage)
# =========================================================================
# Usamos una imagen oficial de Maven que incluye JDK 17 sobre Alpine Linux (una distribución ultraligera).
# Le asignamos el alias 'builder' para poder extraer el resultado final más adelante.
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder

# Establecemos el directorio dentro del contenedor donde se procesará la compilación.
WORKDIR /build

# Copiamos primero el archivo 'pom.xml' para descargar las dependencias necesarias.
COPY pom.xml .

# Descargamos las librerías de Maven en modo offline. Esto optimiza la caché de Docker;
# si el pom.xml no cambia, Docker no volverá a descargar internet en futuras compilaciones.
RUN mvn dependency:go-offline -B

# Copiamos la carpeta completa del código fuente ('src') dentro del contenedor.
COPY src ./src

# Ejecutamos la compilación limpia del proyecto.
# -DskipTests: Omitimos las pruebas unitarias para agilizar el tiempo de despliegue en el pipeline CI/CD.
# Esto generará el archivo ejecutable '.jar' dentro de la ruta '/build/target/'.
RUN mvn clean package -DskipTests


# =========================================================================
# ETAPA 2: Imagen de ejecución en producción (Runtime Stage)
# =========================================================================
# Iniciamos desde una imagen limpia que solo contiene el JRE (Entorno de Ejecución) de Java 17.
# Al no incluir Maven ni compiladores, reducimos el peso de la imagen de ~500MB a ~120MB y evitamos vulnerabilidades.
FROM eclipse-temurin:17-jre-alpine

# Establecemos la carpeta de trabajo final para el microservicio dentro del sistema de archivos del contenedor.
WORKDIR /app

# Definimos variables de entorno por defecto dentro del contenedor.
# Estas pueden ser sobrescritas externamente por GitHub Actions mediante el comando 'docker run -e'.
ENV PORT=8080
ENV EFS_TEMP_PATH=/tmp/guias

# Creamos la carpeta interna '/tmp/guias' destinada a simular el almacenamiento temporal (EFS).
RUN mkdir -p ${EFS_TEMP_PATH}

# Copiamos ÚNICAMENTE el archivo '.jar' compilado en la Etapa 1 desde el alias 'builder'.
# De esta manera la imagen final queda completamente limpia de archivos fuente (.java, .xml, etc).
COPY --from=builder /build/target/*.jar app.jar

# Expone informativamente el puerto en el que escucha el contenedor (buena práctica de documentación en Docker).
EXPOSE ${PORT}

# Comando de ejecución principal al iniciar el contenedor.
# -Dserver.port: Fuerza a Spring Boot a escuchar en el puerto configurado en las variables de entorno.
# -Djava.security.egd: Optimiza la generación de números aleatorios en sistemas Linux (mejora el rendimiento de JWT).
ENTRYPOINT ["java", "-Dserver.port=${PORT}", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]