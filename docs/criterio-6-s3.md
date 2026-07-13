# Criterio 6 — Sube los archivos generados a S3 (10 pts)

## Objetivo del Criterio

Subir los archivos generados (guías de despacho) a S3 automáticamente, con una estructura de carpetas organizada y funcional, integrando el bucket S3 con el microservicio Spring Boot.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| Subir guías generadas a S3 como endpoint REST | ✅ | `POST /api/guias/{id}/subir-s3` |
| Descargar guías con validación de permisos | ✅ | `GET /api/guias/{id}/descargar` (solo rol consulta) |
| Estructura de carpetas organizada | ✅ | `guias/{transportista}/{año}/{mes}/guia-{codigo}.pdf` |
| Bucket S3 público | ✅ | Desbloqueo de acceso público configurado |
| Integración con Spring Cloud AWS | ✅ | Dependencia `spring-cloud-aws-starter-s3` en pom.xml |
| URL de S3 guardada en la entidad | ✅ | Campo `urlS3` en `GuiaDespacho` |

---

## Paso 1: Crear bucket S3 en AWS

### 1.1 Acceder a S3

1. En AWS Console, busca **S3** en la barra de búsqueda
2. Haz clic en **S3** (servicio)

### 1.2 Crear bucket

1. Haz clic en **Create bucket**
2. Completa:
   - **Bucket name:** `guias-despacho-transportista-{tus-iniciales}`
     - Ejemplo: `guias-despacho-transportista-cv`
     - El nombre debe ser **único globalmente**
   - **AWS Region:** `us-east-1` (N. Virginia)
3. Desbloquear acceso público:
   - En **Block Public Access settings for this bucket**, desmarca:
     - ❌ Block *all* public access
   - Marca la casilla de reconocimiento:
     - ✅ I acknowledge that the current settings might result in this bucket and the objects within becoming public
4. El resto de opciones dejarlas por defecto
5. Clic en **Create bucket**

### 1.3 Estructura de carpetas

La estructura se crea automáticamente cuando se suben archivos:

```
guias-despacho-transportista-cv/
└── guias/
    ├── juan_perez/
    │   ├── 2025/
    │   │   ├── 01/
    │   │   │   ├── guia-GD-202501151030-001.pdf
    │   │   │   └── guia-GD-202501151045-002.pdf
    │   │   ├── 02/
    │   │   │   └── guia-GD-202502201500-003.pdf
    │   │   └── ...
    │   └── ...
    └── maria_lopez/
        └── ...
```

**Ventajas de esta estructura:**
- Organizado por transportista, año y mes
- Fácil de navegar manualmente en la consola de AWS
- Cada guía tiene un nombre único (código GD-*)
- Escala bien con muchos archivos

---

## Paso 2: Activar S3 en el código Spring Boot

### 2.1 Verificar dependencia en pom.xml

```xml
<dependency>
    <groupId>io.awspring.cloud</groupId>
    <artifactId>spring-cloud-aws-starter-s3</artifactId>
</dependency>
```

### 2.2 Descomentar configuración en application.yml

**Archivo:** `src/main/resources/application.yml`

```yaml
cloud:
  aws:
    credentials:
      access-key: ${AWS_ACCESS_KEY_ID}
      secret-key: ${AWS_SECRET_ACCESS_KEY}
      session-token: ${AWS_SESSION_TOKEN}
    region:
      static: ${AWS_REGION:us-east-1}
    s3:
      bucket: ${S3_BUCKET_NAME}
```

### 2.3 Descomentar S3Config.java

**Archivo:** `src/main/java/com/transportista/config/S3Config.java`

1. Abrir el archivo
2. Eliminar los bloques de comentario `/*` y `*/` alrededor del código
3. Descomentar las anotaciones `@Value` y `@Bean`
4. El bean `S3Client` se configurará automáticamente

### 2.4 Crear S3Service.java (si no existe)

Crear `src/main/java/com/transportista/service/S3Service.java`:

```java
@Service
@RequiredArgsConstructor
public class S3Service {
    private final S3Client s3Client;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    public String subirGuia(String codigoGuia, String transportista, byte[] contenido) {
        String key = String.format("guias/%s/%d/%02d/guia-%s.pdf",
                transportista.toLowerCase().replace(" ", "_"),
                LocalDate.now().getYear(),
                LocalDate.now().getMonthValue(),
                codigoGuia);

        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        s3Client.putObject(request, RequestBody.fromBytes(contenido));

        return String.format("https://%s.s3.amazonaws.com/%s", bucketName, key);
    }

    public byte[] descargarGuia(String codigoGuia) {
        // Implementar lógica de descarga
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(/* construir key desde codigoGuia */)
                .build();
        return s3Client.getObject(request).readAllBytes();
    }
}
```

### 2.5 Actualizar GuiaDespachoService.subirGuiaAS3()

En el método `subirGuiaAS3(id)`, reemplazar la lógica TODO con la llamada real:

```java
// Antes (placeholder):
// String urlS3 = String.format("s3://...", ...);

// Después (real):
String contenido = generarPdf(guia);  // o generarXml(guia)
String urlS3 = s3Service.subirGuia(
    guia.getCodigoGuia(),
    guia.getTransportista(),
    contenido.getBytes()
);
guia.setUrlS3(urlS3);
```

---

## Paso 3: Crear credenciales de AWS para desarrollo local

Las credenciales de AWS Academy se obtienen del Learner Lab:

1. En AWS Academy, ve a **AWS Details**
2. En la sección **AWS CLI**, copia:
   - `AWS_ACCESS_KEY_ID`
   - `AWS_SECRET_ACCESS_KEY`
   - `AWS_SESSION_TOKEN`

### Configurar como variables de entorno:

```bash
export AWS_ACCESS_KEY_ID=ASIA...
export AWS_SECRET_ACCESS_KEY=abc...
export AWS_SESSION_TOKEN=IQo...
export AWS_REGION=us-east-1
export S3_BUCKET_NAME=guias-despacho-transportista-cv
```

⚠️ Estos valores **CAMBIAN cada vez que inicias el Learner Lab**. Debes actualizarlos en cada sesión.

---

## Paso 4: Probar subida y descarga a S3

### 4.1 Subir una guía a S3

```bash
# Asumiendo que ya creaste una guía con POST /api/guias (id=1)
curl -X POST \
  http://localhost:8080/api/guias/1/subir-s3 \
  -H "Authorization: Bearer {JWT_ADMIN}"
```

**Respuesta esperada:**
```json
{
  "id": 1,
  "codigoGuia": "GD-202501151030-001",
  "urlS3": "https://guias-despacho-transportista-cv.s3.amazonaws.com/guias/juan_perez/2025/01/guia-GD-202501151030-001.pdf",
  ...
}
```

### 4.2 Verificar en la consola de AWS

1. Ve a **S3 → tu bucket**
2. Navega por las carpetas: `guias/` → `juan_perez/` → `2025/` → `01/`
3. Deberías ver el archivo `guia-GD-202501151030-001.pdf`

### 4.3 Descargar una guía

```bash
curl -X GET \
  http://localhost:8080/api/guias/1/descargar \
  -H "Authorization: Bearer {JWT_CONSULTA}"
```

---

## Paso 5: Configurar S3 en el pipeline CI/CD

En el archivo `.github/workflows/deploy.yml`, el paso "Generar application.yml" ya está configurado para crear las credenciales de AWS automáticamente:

```yaml
- name: Generar archivo application.yml con credenciales AWS
  run: |
    cat > src/main/resources/application.yml << 'YAMLEOF'
    cloud:
      aws:
        credentials:
          access-key: ${{ secrets.AWS_ACCESS_KEY_ID }}
          secret-key: ${{ secrets.AWS_SECRET_ACCESS_KEY }}
          session-token: ${{ secrets.AWS_SESSION_TOKEN }}
        region:
          static: us-east-1
        s3:
          bucket: ${{ vars.S3_BUCKET_NAME || 'guias-despacho-transportista' }}
    YAMLEOF
```

Esto genera el archivo con las credenciales reales al momento del despliegue.

---

## Verificación del Criterio

### Evidencia para la documentación Word:

1. Captura de la consola S3 mostrando el bucket creado
2. Captura del bucket con acceso público desbloqueado
3. Captura de Postman ejecutando `POST /api/guias/{id}/subir-s3` → 200 OK
4. Captura de la respuesta mostrando el campo `urlS3` completado
5. Captura de la consola S3 mostrando el archivo subido en la estructura de carpetas
6. Captura de la estructura de carpetas: `guias/{transportista}/{año}/{mes}/`
7. Captura de Postman ejecutando `GET /api/guias/{id}/descargar` (con token consulta) → 200
8. Captura de Postman intentando descargar con token admin → 403 Forbidden
9. Captura del código `S3Service.java`
10. Captura del pipeline CI/CD ejecutándose en GitHub Actions

---

## Checklist de Verificación

- [ ] Bucket S3 creado en AWS (nombre único global)
- [ ] Acceso público desbloqueado en el bucket
- [ ] Dependencia `spring-cloud-aws-starter-s3` en pom.xml
- [ ] Configuración cloud.aws descomentada en application.yml
- [ ] `S3Config.java` descomentado y funcional
- [ ] `S3Service.java` creado con métodos subir/descargar
- [ ] `GuiaDespachoService.subirGuiaAS3()` actualizado con lógica real
- [ ] Variables de entorno AWS configuradas localmente
- [ ] Endpoint `POST /api/guias/{id}/subir-s3` probado → 200 OK
- [ ] Endpoint `GET /api/guias/{id}/descargar` probado (rol consulta) → 200 OK
- [ ] Estructura de carpetas verificada en consola S3
- [ ] Campo `urlS3` guardado correctamente en la entidad
- [ ] GitHub Secrets AWS configurados para CI/CD
- [ ] Pipeline genera application.yml con credenciales automáticamente
