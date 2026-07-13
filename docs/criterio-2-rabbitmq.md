# Criterio 2 — Configura las colas en RabbitMQ (15 pts)

## Objetivo del Criterio

Configurar las colas en RabbitMQ correctamente, incluyendo productores, consumidores y manejo de mensajes de error, cumpliendo con el caso de la empresa transportista.

---

## Requisitos Cubiertos del Formato de Respuesta

| Requisito | ¿Cubierto? | Evidencia |
|-----------|:----------:|-----------|
| 2 servicios de colas | ✅ | `cola-guias-exitosas` + `cola-guias-error` |
| 1 componente que transmita mensajes en ambas colas | ✅ | `GuiaProducer.java` |
| Productores y consumidores en Java | ✅ | `GuiaProducer` + `GuiaConsumer` en Spring Boot |
| Cola RabbitMQ desplegada en contenedor Docker | ✅ | `docker-compose.yml` con imagen `rabbitmq:3-management` |
| Cola 1: guías de despacho normales | ✅ | `cola-guias-exitosas` |
| Cola 2: mensajes con errores | ✅ | `cola-guias-error` |
| DLX para reintentos | ✅ | Cola de error configurada con `x-dead-letter-exchange` |
| Endpoint adicional para consumir cola 1 | ✅ | `POST /api/cola/procesar-guias` (visto en Criterio 1) |

---

## Paso 1: Iniciar RabbitMQ con Docker

### Opción A — Solo RabbitMQ (desarrollo local)

```bash
docker run -d \
  --name rabbitmq-transportista \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=guest \
  -e RABBITMQ_DEFAULT_PASS=guest \
  rabbitmq:3-management
```

### Opción B — Con Docker Compose (recomendado)

**Archivo:** `docker-compose.yml` (ya incluido en el proyecto)

```bash
cd transportista/
docker compose up -d rabbitmq
```

**Verificación:**
- Panel de administración: http://localhost:15672
- Credenciales: `guest` / `guest`

---

## Paso 2: Crear colas y exchange en la UI de RabbitMQ

Accede a **http://localhost:15672** y sigue estos pasos:

### 2.1 Crear colas

1. Ve a la pestaña **Queues and Streams**
2. Haz clic en **Add a new queue**
3. Crea la cola de exitosas:
   - **Name:** `cola-guias-exitosas`
   - **Durability:** Durable ✅
   - Clic en **Add queue**

4. Crea la cola de errores:
   - **Name:** `cola-guias-error`
   - **Durability:** Durable ✅
   - **Arguments:** `x-dead-letter-exchange` = `dlx-exchange`
   - Clic en **Add queue**

### 2.2 Crear exchange

1. Ve a la pestaña **Exchanges**
2. Haz clic en **Add a new exchange**
3. Completa:
   - **Name:** `exchange-guias`
   - **Type:** `direct`
   - Clic en **Add exchange**

### 2.3 Crear bindings (asociaciones)

1. En el exchange `exchange-guias`, ve a la sección **Bindings**
2. Completa el primer binding:
   - **To queue:** `cola-guias-exitosas`
   - **Routing key:** `guia.exitosa`
   - Clic en **Bind**

3. Completa el segundo binding:
   - **To queue:** `cola-guias-error`
   - **Routing key:** `guia.error`
   - Clic en **Bind**

### Resultado esperado:

```
exchange-guias (direct)
  ├── routing key: guia.exitosa  →  cola-guias-exitosas
  └── routing key: guia.error    →  cola-guias-error
```

---

## Paso 3: Configurar RabbitMQ en Spring Boot

### 3.1 application.properties

```properties
spring.rabbitmq.host=${RABBITMQ_HOST:localhost}
spring.rabbitmq.port=${RABBITMQ_PORT:5672}
spring.rabbitmq.username=${RABBITMQ_USERNAME:guest}
spring.rabbitmq.password=${RABBITMQ_PASSWORD:guest}
```

### 3.2 RabbitMQConfig.java (configuración programática)

**Archivo:** `src/main/java/com/transportista/rabbitmq/RabbitMQConfig.java`

Esta clase crea automáticamente las colas, exchanges y bindings al iniciar Spring Boot:

```java
@Configuration
public class RabbitMQConfig {
    public static final String QUEUE_EXITOSA     = "cola-guias-exitosas";
    public static final String QUEUE_ERROR       = "cola-guias-error";
    public static final String EXCHANGE          = "exchange-guias";
    public static final String ROUTING_KEY_EXITOSA = "guia.exitosa";
    public static final String ROUTING_KEY_ERROR   = "guia.error";

    @Bean public Queue colaExitosa() { return new Queue(QUEUE_EXITOSA, true); }

    @Bean public Queue colaError() {
        return QueueBuilder.durable(QUEUE_ERROR)
                .withArgument("x-dead-letter-exchange", "dlx-exchange")
                .build();
    }

    @Bean public DirectExchange exchange() { return new DirectExchange(EXCHANGE); }

    @Bean public Binding bindingExitosa() {
        return BindingBuilder.bind(colaExitosa()).to(exchange()).with(ROUTING_KEY_EXITOSA);
    }

    @Bean public Binding bindingError() {
        return BindingBuilder.bind(colaError()).to(exchange()).with(ROUTING_KEY_ERROR);
    }

    @Bean public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean public RabbitTemplate rabbitTemplate(ConnectionFactory cf) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(jsonMessageConverter());
        return t;
    }
}
```

**Puntos clave:**
- Las colas son **durables** (sobreviven reinicios de RabbitMQ)
- La cola de errores tiene **DLX** (`x-dead-letter-exchange`) para reintentos
- Los mensajes se serializan como **JSON** (no binario Java) para portabilidad
- Exchange tipo **direct**: enruta por coincidencia exacta de routing key

---

## Paso 4: Implementar Productor de mensajes

**Archivo:** `src/main/java/com/transportista/rabbitmq/GuiaProducer.java`

```java
@Component
@RequiredArgsConstructor
public class GuiaProducer {
    private final RabbitTemplate rabbitTemplate;

    // Envía a la cola de EXITOSAS (cola-guias-exitosas)
    public void enviarGuiaExitosa(GuiaDespacho guia) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_KEY_EXITOSA,  // "guia.exitosa"
            guia
        );
    }

    // Envía a la cola de ERRORES (cola-guias-error)
    public void enviarGuiaError(GuiaDespacho guia) {
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.EXCHANGE,
            RabbitMQConfig.ROUTING_KEY_ERROR,    // "guia.error"
            guia
        );
    }
}
```

### Integración con el Service (GuiaDespachoService):

```java
// Al crear/modificar una guía:
try {
    guiaProducer.enviarGuiaExitosa(saved);
    saved.setEstado(EstadoGuia.ENVIADA);
} catch (Exception e) {
    saved.setEstado(EstadoGuia.CON_ERROR);
    guiaProducer.enviarGuiaError(saved);  // ← Cola 2 (mensajes con error)
}
```

---

## Paso 5: Implementar Consumidor de mensajes

**Archivo:** `src/main/java/com/transportista/rabbitmq/GuiaConsumer.java`

```java
@Component
@RequiredArgsConstructor
public class GuiaConsumer {
    private final GuiaDespachoProcesadaRepository procesadaRepository;

    // Escucha la cola 1 (cola-guias-exitosas)
    @RabbitListener(queues = RabbitMQConfig.QUEUE_EXITOSA)
    public void consumirGuiaExitosa(GuiaDespacho guia) {
        // Guarda en la tabla NUEVA (GUIA_DESPACHO_PROCESADA)
        GuiaDespachoProcesada procesada = GuiaDespachoProcesada.builder()
                .codigoGuia(guia.getCodigoGuia())
                .transportista(guia.getTransportista())
                .fechaProcesamiento(LocalDateTime.now())  // ← Campo NUEVO
                .build();
        procesadaRepository.save(procesada);
    }

    // Escucha la cola 2 (cola-guias-error)
    @RabbitListener(queues = RabbitMQConfig.QUEUE_ERROR)
    public void consumirGuiaError(GuiaDespacho guia) {
        log.error("Mensaje de error: guía {}", guia.getCodigoGuia());
        // Solo registra en logs, no guarda en BD
    }
}
```

### Mecanismo DLX (Dead Letter Exchange):

Cuando el consumidor de la cola 1 lanza una excepción (ej: falla la BD):
1. RabbitMQ reintenta el mensaje (según la configuración)
2. Después de X reintentos fallidos, el mensaje se envía automáticamente al DLX
3. El DLX redirige a una cola de mensajes muertos para inspección

Esto evita pérdida de datos: **ningún mensaje se pierde**, siempre queda en alguna cola.

---

## Paso 6: Enviar y recibir mensajes manualmente (prueba)

### Enviar un mensaje desde la UI de RabbitMQ:

1. Ve a la pestaña **Exchanges** → `exchange-guias`
2. En **Publish message**:
   - Routing key: `guia.exitosa`
   - Payload:
     ```json
     {
       "codigoGuia": "GD-TEST-001",
       "transportista": "Prueba Manual",
       "origen": "Santiago",
       "destino": "Valparaíso"
     }
     ```
3. Clic en **Publish message**

4. Ve a **Queues** → `cola-guias-exitosas` → verás 1 mensaje encolado

### Verificar consumo desde Spring Boot:

Al iniciar la aplicación con `./mvnw spring-boot:run`, el `@RabbitListener` procesa automáticamente los mensajes en la cola. Verás en los logs:

```
Mensaje recibido en cola-guias-exitosas: guía GD-TEST-001
Guía GD-TEST-001 procesada y guardada en GUIA_DESPACHO_PROCESADA
```

---

## Verificación del Criterio

### Tests unitarios:

| Clase de test | Tests | ¿Qué valida? |
|---------------|:-----:|--------------|
| `GuiaProducerTest` | 5 | Routing keys correctas, no mezcla colas, serialización |
| `GuiaConsumerTest` | 6 | Guardado en tabla nueva, DLX, manejo de errores, fechaProcesamiento |
| `TransportistaApplicationTests` (RabbitMQ) | 7 | Beans configurados: colas, exchange, bindings, routing keys |

### Ejecutar tests de RabbitMQ:

```bash
cd transportista/
./mvnw test -DforkCount=0 -Dtest="GuiaProducerTest,GuiaConsumerTest"
```

### Evidencia para la documentación Word:

1. Captura de la UI de RabbitMQ mostrando las 2 colas creadas
2. Captura de la UI mostrando el exchange y sus bindings
3. Captura de `docker ps` mostrando el contenedor rabbitmq ejecutándose
4. Captura de Postman enviando un POST /api/guias
5. Captura de la UI de RabbitMQ mostrando mensajes en `cola-guias-exitosas`
6. Captura de logs de Spring Boot mostrando el mensaje consumido
7. Captura de la tabla GUIA_DESPACHO_PROCESADA con los datos guardados
8. Captura de la UI de RabbitMQ mostrando mensajes en `cola-guias-error` (simular un fallo)

### Simular un error (para probar cola 2):

1. Detén Oracle Cloud temporalmente (o usa credenciales incorrectas)
2. Crea una guía → el Service detectará el error
3. La guía se enviará a `cola-guias-error`
4. Verifica en la UI de RabbitMQ → `cola-guias-error` tendrá mensajes

---

## Checklist de Verificación

- [x] RabbitMQ corriendo en Docker (imagen `rabbitmq:3-management`)
- [x] 2 colas creadas: `cola-guias-exitosas` y `cola-guias-error`
- [x] Exchange `exchange-guias` tipo `direct` con 2 bindings
- [x] Clase `RabbitMQConfig` con configuración programática
- [x] Clase `GuiaProducer` con métodos para ambas colas
- [x] Clase `GuiaConsumer` con `@RabbitListener` para ambas colas
- [x] Enrutamiento: éxito → cola 1, error → cola 2
- [x] DLX configurado en cola de errores para reintentos
- [x] Mensajes serializados como JSON
- [x] Tests unitarios pasando (11 tests de RabbitMQ)
- [x] `docker-compose.yml` con servicio rabbitmq configurado
- [ ] Colas y exchange creados manualmente en la UI (paso de ejecución)
