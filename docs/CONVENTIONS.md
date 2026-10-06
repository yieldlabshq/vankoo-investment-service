# Convenciones

## Paquetes y nombres

- El package raíz es `com.liquilabs.vankoo.investment`.
- Los nombres de clases y records están en inglés y en `PascalCase`; métodos, variables y
  parámetros están en `camelCase`.
- Usa sufijos por responsabilidad: `Command`, `Query`, `Event`, `Service`, `ServiceImpl`,
  `Repository`, `Entity`, `Resource`, `Assembler`, `Controller` y `Consumer`.
- Los conceptos del dominio mantienen su vocabulario actual: `Auction`, `Partition`,
  `InvestorParticipation`, `Invoice`, `RiskScore` y `Money`.

## Código de dominio

- Crea y modifica entidades hijas desde su aggregate root; no expongas mutación directa de
  colecciones de un agregado.
- Usa `Money`, `Percentage`, IDs tipados y enums existentes para modelar reglas. No dupliques
  conversiones o validaciones de negocio en recursos, assemblers o handlers.
- Prefiere constructores y métodos expresivos del dominio sobre setters públicos en agregados.
- Registra eventos con nombres en pasado (`AuctionCreatedEvent`, no `CreateAuctionEvent`) después
  de que el hecho del dominio ocurre.

## Aplicación e interfaces

- Los command services son transaccionales cuando persisten cambios.
- Los query services devuelven datos de lectura y nunca tienen efectos de escritura.
- Los controllers devuelven códigos HTTP explícitos y delegan la transformación de recursos a
  assemblers.
- Los consumers de Kafka registran contexto útil y delegan la conversión a un assembler; no
  replican reglas de negocio.
- Mantén los handlers de eventos centrados en una reacción. Si un handler requiere una decisión
  de dominio, introduce un command y deja que el agregado la resuelva.

## Calidad y configuración

- Usa inyección por constructor; no uses inyección de campos.
- No agregues `System.out`, TODOs sin feature asociada ni código de depuración.
- Toda configuración variable usa propiedades y variables de entorno. Nunca incluyas secretos,
  URLs de producción o archivos `.env` en cambios versionados.
- Añade o actualiza pruebas bajo `src/test` para cada cambio de comportamiento del dominio,
  command service o proyección.
