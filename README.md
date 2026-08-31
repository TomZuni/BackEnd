# Migración Batch Banco XYZ (Semana 3 - PBY2203)

## Objetivo del proyecto
Migrar y modernizar 3 procesos legacy del Banco XYZ usando **Spring Batch**:

1. **Reporte de Transacciones Diarias** — detecta anomalías (montos inválidos, duplicados) y genera un resumen.
2. **Cálculo de Intereses Mensuales** — aplica interés según tipo de cuenta y actualiza el saldo final.
3. **Generación de Estados de Cuenta Anuales** — compila los movimientos del año por cuenta y genera un informe de auditoría.

Los datos de origen vienen del repositorio legacy real: `https://github.com/KariVillagran/bank_legacy_data` (carpeta `data/semana_3`), que incorpora los casos de inconsistencia definidos para esta semana: fechas en 4 formatos distintos mezclados en el mismo archivo (`yyyy-MM-dd`, `yyyy/MM/dd`, `dd-MM-yyyy`, `dd/MM/yyyy`) incluyendo fechas inexistentes (ej. `2024-13-01`), tipos de transacción/cuenta inválidos (`invalid`, `desconocido`, `unknown`, `-1`), montos y saldos vacíos o negativos, edades fuera de rango, y variantes de tipo de movimiento anual no reconocidas (`depósito` con tilde, `pago`).

## Estructura del código
```
src/main/java/cl/duoc/bankxyz/migracion/
├── BankxyzMigracionBatchApplication.java   Clase principal
├── dtos/            Forma cruda de cada línea del CSV (todo String, sin conversión automática)
├── entities/         Forma ya validada/calculada que se persiste en H2
├── processors/        Un ItemProcessor por proceso: valida y calcula (no descarta filas con error, las marca RECHAZADA)
├── tasklets/          Steps de resumen (se ejecutan después del Step principal de cada Job)
├── listeners/         BatchStepListener (logs de rendimiento + skips) y BatchRetryListener (Semana 2)
├── config/           Definición de los 3 Jobs, sus Steps, Readers y Writers (multithreading + fault tolerance en Semana 2)
├── controllers/       Endpoints REST para disparar cada Job y consultar resultados
└── services/          Dispara el JobLauncher y traduce errores a excepción de negocio
src/main/resources/
├── application.properties   Datasource H2, puerto 8081, rutas de los CSV
├── schema.sql               DDL de las tablas de negocio (se recrean en cada arranque)
└── data/                     transacciones.csv, intereses.csv, cuentas_anuales.csv
```

## Cómo ejecutar
Requiere Java 17 y Maven.

```bash
mvn spring-boot:run
```

La app queda escuchando en `http://localhost:8081`. Los Jobs **no** se disparan solos al iniciar
(`spring.batch.job.enabled=false`): hay que dispararlos manualmente por HTTP.

Consola H2: `http://localhost:8081/h2-console`
JDBC URL: `jdbc:h2:mem:bankxyz_batch` — usuario `sa`, sin contraseña.

## Endpoints 

| Método | Endpoint | Qué hace |
|---|---|---|
| POST | `/api/transacciones/procesar` | Dispara el Job 1 (transacciones diarias) |
| GET  | `/api/resultados/transacciones` | Detalle de cada transacción procesada |
| GET  | `/api/resultados/transacciones/resumen` | Resumen de anomalías y totales |
| POST | `/api/intereses/procesar` | Dispara el Job 2 (intereses mensuales) |
| GET  | `/api/resultados/intereses` | Detalle de interés/saldo final por cuenta |
| POST | `/api/cuentas-anuales/procesar` | Dispara el Job 3 (estados de cuenta anuales) |
| GET  | `/api/resultados/cuentas-anuales` | Detalle de cada movimiento procesado |
| GET  | `/api/resultados/cuentas-anuales/resumen` | Informe anual por cuenta (depósitos, retiros, saldo neto) |



## Reglas de negocio aplicadas
- **Fechas**: los 3 processors aceptan indistintamente `yyyy-MM-dd`, `yyyy/MM/dd`, `dd-MM-yyyy` y
  `dd/MM/yyyy` (los 4 formatos que trae la data legacy mezclados en el mismo archivo) y normalizan a
  `LocalDate`. Internamente los patrones usan `'u'` (año ISO) en vez de `'y'` (año-de-era) — con
  `ResolverStyle.STRICT`, `'y'` exige además una Era que nunca se provee y la fecha no llega a resolver
  aunque el número de año sea correcto. `ResolverStyle.STRICT` sigue siendo clave para el otro caso: una
  fecha inexistente como `2024-13-01` (mes 13) no se "corrige" silenciosamente, queda RECHAZADA.
- **Transacciones**: se rechaza si el monto es ≤ 0, si el tipo no es `debito`/`credito` (cubre las
  variantes inválidas `invalid`/`desconocido` del CSV), si la fecha no es parseable, o si la fila es un
  duplicado exacto (misma fecha + monto + tipo ya visto).
- **Intereses**: tasa mensual asumida — `ahorro 0.5%`, `préstamo 1.5%`, `hipoteca 1.0%` (no vienen
  especificadas en el CSV legacy, se documentan aquí como supuesto). Se rechaza si el tipo de cuenta no
  es uno de los 3 anteriores (cubre `unknown`/`-1`), si la edad está fuera de 18–120, si el saldo está
  vacío/no numérico/negativo, o si el registro es un duplicado del mismo cliente.
- **Cuentas anuales**: se rechaza si la fecha no es parseable, si el tipo de movimiento no es
  `deposito`/`retiro`/`compra` (cubre las variantes inválidas `depósito` con tilde y `pago` del CSV de
  esta semana), si falta la descripción, si el monto es cero, o si un `deposito` viene con monto
  negativo (un depósito siempre debe sumar).