# Verificación

## Prerequisitos

- JDK 25 disponible en `JAVA_HOME` y en el `PATH`.
- Maven disponible en el `PATH`.

## Verificación obligatoria

Desde la raíz del repositorio, ejecuta:

```bash
mvn test
```

Una feature solo puede pasar a revisión con salida 0. Registra el comando, su resultado y las
pruebas relevantes en `progress.md`.

## Ejecución local opcional

Para iniciar el servicio de forma local se necesitan Oracle y Kafka según
`src/main/resources/application-dev.yaml`. Los valores se configuran mediante variables de
entorno `INVESTMENT_DB_*`, `KAFKA_*` y, si corresponde, `JWT_*`. No incluyas sus valores reales
en documentación, commits ni salidas de herramientas.

```bash
mvn spring-boot:run
```
