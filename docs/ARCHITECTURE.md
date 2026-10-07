# Arquitectura

## Contexto y stack

`vankoo-investment-service` gestiona subastas de facturas e inversiones. Está construido con
Java 25 y Spring Boot, usa Spring Data JPA con Oracle para persistencia, Spring Cloud Stream
con Kafka para integración y Springdoc/Scalar para la API HTTP.

El proyecto adopta DDD pragmático con CQRS y eventos de dominio. La separación por paquetes es
la fuente de verdad para esta arquitectura.

## Capas y dependencias

| Capa | Ubicación | Responsabilidad | Dependencias permitidas |
| --- | --- | --- | --- |
| Dominio | `domain/` | Agregados, entidades, value objects, commands, queries, eventos y contratos de servicio | Frameworks de persistencia ya presentes; nunca `application`, `infrastructure` ni `interfaces` |
| Aplicación | `application/internal/` | Orquesta comandos, consultas y reacciones a eventos | Dominio e infraestructura necesaria para persistir o leer |
| Infraestructura | `infrastructure/` | Repositorios JPA, entidades de proyección y configuración técnica | Dominio y frameworks técnicos |
| Interfaces | `interfaces/` | Controladores REST, recursos, assemblers y consumidores de integración | Commands/queries y servicios de dominio; no reglas de negocio |

La infraestructura actual se inyecta directamente en servicios de aplicación mediante
repositorios Spring Data. No se debe introducir una abstracción adicional salvo que una
necesidad concreta la justifique.

## Modelo de dominio

- `Auction` es el aggregate root de una subasta. Es el único lugar que modifica su estado y
  administra sus `Partition`.
- Identificadores, dinero, porcentajes, score y estados son value objects o enums del dominio;
  no se reemplazan por primitivas en la lógica de negocio.
- Las reglas de inversión —mínimo, límite de financiamiento, porcentaje de participación y
  cambio de estado— viven en `Auction`.
- Las excepciones de reglas de negocio se expresan como excepciones Java claras desde el
  agregado y se traducen en la interfaz si se necesita una respuesta HTTP o de integración.

## CQRS

- Un `...Command` expresa una intención de cambio y se procesa en
  `application/internal/commandservices` a través de un `...CommandService`.
- Un `...Query` expresa una lectura y se procesa en
  `application/internal/queryservices` a través de un `...QueryService`.
- Un query service no guarda, borra ni modifica agregados. Cuando el caso de uso es de
  marketplace, lee la proyección `AuctionMarketplaceViewEntity` mediante su repositorio.
- REST y Kafka convierten sus DTOs o eventos de integración a command/query usando assemblers;
  no llaman repositorios directamente.

## Eventos de dominio e integración

`Auction` extiende `AbstractAggregateRoot` y registra eventos mediante `registerEvent`. Los
eventos actuales son `AuctionCreatedEvent`, `PartitionAddedEvent` y
`AuctionFullyFundedEvent`.

- Un evento de dominio describe un hecho ya ocurrido dentro del agregado; se registra desde el
  agregado antes de persistirlo.
- Los listeners internos se implementan en `application/internal/eventhandlers` con
  `@EventListener`. Mantienen proyecciones de lectura o coordinan trabajo de aplicación; no
  contienen las invariantes de `Auction`.
- Los eventos de Kafka son contratos de integración y pertenecen a `interfaces/events`. No son
  eventos de dominio. Un consumer los transforma a un command y lo delega al servicio
  correspondiente.
- Publicar un evento de dominio hacia Kafka requiere una feature explícita que defina contrato,
  entrega, idempotencia y manejo de errores; no se hace de forma implícita desde un listener.

## Persistencia y configuración

- Los agregados JPA se guardan mediante repositorios de
  `infrastructure/persistence/jpa/repositories`.
- Las tablas de lectura se modelan por separado en `infrastructure/persistence/jpa/views`.
- El perfil `dev` consume host, puerto, usuario y contraseña desde variables de entorno. No se
  versionan secretos ni archivos `.env`.
- `spring.jpa.hibernate.ddl-auto=update` puede cambiar un esquema conectado: toda modificación
  de entidad o del esquema requiere la aprobación previa indicada por la skill
  `database-schema-change`.

## Marketplace

La propiedad, consistencia eventual, consulta, stream de integración y procedimiento de replay del
read model están definidos en [`MARKETPLACE_READ_MODEL.md`](MARKETPLACE_READ_MODEL.md).

Mientras no exista un servicio de riesgo, las subastas se evalúan con un sustituto temporal
descrito en [`SIMULATED_RISK_EVALUATION.md`](SIMULATED_RISK_EVALUATION.md).

La entrega at-least-once de sus eventos de integración mediante Oracle y Kafka se describe en
[`AUCTION_OUTBOX.md`](AUCTION_OUTBOX.md).
