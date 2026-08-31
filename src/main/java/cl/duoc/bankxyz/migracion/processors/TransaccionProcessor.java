package cl.duoc.bankxyz.migracion.processors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.duoc.bankxyz.migracion.dtos.TransaccionDTO;
import cl.duoc.bankxyz.migracion.entities.TransaccionEntity;

/**
 * ItemProcessor del proceso "Reporte de Transacciones Diarias".
 *
 * A diferencia de un pipeline "feliz", este processor NO descarta los
 * registros con problemas (no retorna null): los clasifica como
 * VALIDA/RECHAZADA y persiste igual, dejando el motivo del rechazo en la
 * columna motivo_rechazo. Esto permite auditar en la base de datos (y en la
 * respuesta de Postman) cuantos y cuales registros tenian anomalias, tal
 * como pide el enunciado ("detectar anomalias y generar un resumen").
 *
 * Es @StepScope (se declara asi en la configuracion del Job) para que el
 * Set de duplicados se reinicie en cada ejecucion del Job, en vez de
 * arrastrar estado entre ejecuciones distintas.
 */
public class TransaccionProcessor implements ItemProcessor<TransaccionDTO, TransaccionEntity> {

    /**
     * La data legacy de esta semana trae 4 formatos de fecha distintos
     * mezclados en el mismo archivo (uuuu-MM-dd, uuuu/MM/dd, dd-MM-uuuu y
     * dd/MM/uuuu). Se intenta parsear con cada uno en orden; el primero que
     * calce gana. Se usa el patron 'u' (año ISO) y no 'y' (año-de-era):
     * con ResolverStyle.STRICT, 'y' exige ademas una Era (d.C./a.C.) que
     * nunca se provee, y la fecha nunca terminaba de resolverse aunque el
     * numero de año fuera correcto. ResolverStyle.STRICT si sigue siendo
     * clave para el otro caso: sin el, "2024-13-01" (mes 13) se
     * "normalizaria" a una fecha de otro mes en vez de fallar, y ese es
     * justamente uno de los casos de inconsistencia a detectar.
     */
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    private final Set<String> clavesVistas = new HashSet<>();

    @Override
    public TransaccionEntity process(TransaccionDTO dto) {
        Long id = parsearIdSeguro(dto.getId());

        // 1) Validar tipo
        String tipo = dto.getTipo() == null ? "" : dto.getTipo().trim().toLowerCase();
        if (!tipo.equals("debito") && !tipo.equals("credito")) {
            return rechazar(id, null, null, dto.getTipo(), "Tipo de transaccion invalido: '" + dto.getTipo() + "'");
        }

        // 2) Validar y normalizar fecha (el legacy mezcla varios formatos)
        LocalDate fecha = parsearFecha(dto.getFecha());
        if (fecha == null) {
            return rechazar(id, null, null, tipo, "Formato de fecha invalido: '" + dto.getFecha() + "'");
        }

        // 3) Validar monto
        BigDecimal monto;
        try {
            monto = new BigDecimal(dto.getMonto().trim());
        } catch (Exception ex) {
            return rechazar(id, fecha, null, tipo, "Monto no numerico: '" + dto.getMonto() + "'");
        }
        if (monto.compareTo(BigDecimal.ZERO) <= 0) {
            return rechazar(id, fecha, monto, tipo, "Monto invalido (debe ser mayor a 0)");
        }

        // 4) Detectar duplicados (misma fecha + monto + tipo ya procesados en este Job)
        String clave = fecha + "|" + monto.stripTrailingZeros().toPlainString() + "|" + tipo;
        if (!clavesVistas.add(clave)) {
            return rechazar(id, fecha, monto, tipo, "Registro duplicado (fecha, monto y tipo repetidos)");
        }

        return TransaccionEntity.builder()
                .transaccionId(id)
                .fecha(fecha)
                .monto(monto)
                .tipo(tipo)
                .estado("VALIDA")
                .motivoRechazo(null)
                .build();
    }

    private TransaccionEntity rechazar(Long id, LocalDate fecha, BigDecimal monto, String tipo, String motivo) {
        return TransaccionEntity.builder()
                .transaccionId(id)
                .fecha(fecha)
                .monto(monto)
                .tipo(tipo)
                .estado("RECHAZADA")
                .motivoRechazo(motivo)
                .build();
    }

    private Long parsearIdSeguro(String rawId) {
        try {
            return Long.parseLong(rawId.trim());
        } catch (Exception ex) {
            return null;
        }
    }

    private LocalDate parsearFecha(String rawFecha) {
        if (rawFecha == null || rawFecha.isBlank()) {
            return null;
        }
        String valor = rawFecha.trim();
        for (DateTimeFormatter formato : FORMATOS_FECHA) {
            try {
                return LocalDate.parse(valor, formato);
            } catch (DateTimeParseException ex) {
                // se intenta con el siguiente formato conocido
            }
        }
        return null; // ningun formato calzo (o la fecha no existe, ej. mes 13)
    }
}
