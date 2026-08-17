package cl.duoc.bankxyz.migracion.processors;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.duoc.bankxyz.migracion.dtos.InteresDTO;
import cl.duoc.bankxyz.migracion.entities.InteresEntity;

/**
 * ItemProcessor del proceso "Calculo de Intereses Mensuales".
 *
 * Aplica una tasa de interes mensual segun el tipo de cuenta y calcula el
 * saldo final (saldo original + interes). Igual que en TransaccionProcessor,
 * los registros con problemas se marcan como RECHAZADA en vez de
 * descartarse, para dejar evidencia auditable.
 *
 * Reglas de negocio asumidas (no vienen especificadas en el CSV legacy):
 *   - ahorro:   0.5% mensual
 *   - prestamo: 1.5% mensual
 *   - hipoteca: 1.0% mensual
 */
public class InteresProcessor implements ItemProcessor<InteresDTO, InteresEntity> {

    private static final Map<String, BigDecimal> TASAS_POR_TIPO = Map.of(
            "ahorro", new BigDecimal("0.005"),
            "prestamo", new BigDecimal("0.015"),
            "hipoteca", new BigDecimal("0.010"));

    private static final int EDAD_MINIMA = 18;
    private static final int EDAD_MAXIMA = 120;

    private final Set<String> clavesVistas = new HashSet<>();

    @Override
    public InteresEntity process(InteresDTO dto) {
        Long cuentaId = parsearIdSeguro(dto.getCuentaId());
        String nombre = dto.getNombre() == null ? "" : dto.getNombre().trim();
        String tipo = dto.getTipo() == null ? "" : dto.getTipo().trim().toLowerCase();

        // 1) Validar tipo de cuenta (define la tasa a aplicar)
        BigDecimal tasa = TASAS_POR_TIPO.get(tipo);
        if (tasa == null) {
            return rechazar(cuentaId, nombre, tipo, null, null, "Tipo de cuenta invalido: '" + dto.getTipo() + "'");
        }

        // 2) Validar edad
        Long edadLong = parsearIdSeguro(dto.getEdad());
        Integer edad = edadLong == null ? null : edadLong.intValue();
        if (edad == null || edad < EDAD_MINIMA || edad > EDAD_MAXIMA) {
            return rechazar(cuentaId, nombre, tipo, edad, null,
                    "Edad fuera de rango valido (" + EDAD_MINIMA + "-" + EDAD_MAXIMA + "): '" + dto.getEdad() + "'");
        }

        // 3) Validar saldo (vacio o negativo)
        if (dto.getSaldo() == null || dto.getSaldo().isBlank()) {
            return rechazar(cuentaId, nombre, tipo, edad, null, "Saldo vacio");
        }
        BigDecimal saldo;
        try {
            saldo = new BigDecimal(dto.getSaldo().trim());
        } catch (Exception ex) {
            return rechazar(cuentaId, nombre, tipo, edad, null, "Saldo no numerico: '" + dto.getSaldo() + "'");
        }
        if (saldo.compareTo(BigDecimal.ZERO) < 0) {
            return rechazar(cuentaId, nombre, tipo, edad, saldo, "Saldo negativo");
        }

        // 4) Detectar duplicados (mismo nombre + saldo + edad + tipo ya procesados)
        String clave = nombre.toLowerCase() + "|" + saldo.stripTrailingZeros().toPlainString() + "|" + edad + "|" + tipo;
        if (!clavesVistas.add(clave)) {
            return rechazar(cuentaId, nombre, tipo, edad, saldo, "Registro duplicado (mismo cliente repetido)");
        }

        // 5) Calcular interes y saldo final
        BigDecimal interes = saldo.multiply(tasa).setScale(2, RoundingMode.HALF_UP);
        BigDecimal saldoFinal = saldo.add(interes).setScale(2, RoundingMode.HALF_UP);

        return InteresEntity.builder()
                .cuentaId(cuentaId)
                .nombre(nombre)
                .tipoCuenta(tipo)
                .edad(edad)
                .saldoOriginal(saldo)
                .tasaInteres(tasa)
                .interesCalculado(interes)
                .saldoFinal(saldoFinal)
                .estado("VALIDA")
                .motivoRechazo(null)
                .build();
    }

    private InteresEntity rechazar(Long cuentaId, String nombre, String tipo, Integer edad, BigDecimal saldo, String motivo) {
        return InteresEntity.builder()
                .cuentaId(cuentaId)
                .nombre(nombre)
                .tipoCuenta(tipo)
                .edad(edad)
                .saldoOriginal(saldo)
                .tasaInteres(null)
                .interesCalculado(null)
                .saldoFinal(null)
                .estado("RECHAZADA")
                .motivoRechazo(motivo)
                .build();
    }

    private Long parsearIdSeguro(String raw) {
        try {
            return Long.parseLong(raw.trim());
        } catch (Exception ex) {
            return null;
        }
    }
}
