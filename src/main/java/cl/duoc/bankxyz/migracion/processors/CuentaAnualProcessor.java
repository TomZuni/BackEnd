package cl.duoc.bankxyz.migracion.processors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;
import java.util.Set;

import org.springframework.batch.item.ItemProcessor;

import cl.duoc.bankxyz.migracion.dtos.CuentaAnualDTO;
import cl.duoc.bankxyz.migracion.entities.CuentaAnualEntity;

/**
 * ItemProcessor del proceso "Generacion de Estados de Cuenta Anuales".
 *
 * Valida cada movimiento del historial anual de una cuenta: fecha, monto y
 * descripcion. Al igual que los otros processors, no descarta los
 * registros invalidos, los marca como RECHAZADA con su motivo.
 */
public class CuentaAnualProcessor implements ItemProcessor<CuentaAnualDTO, CuentaAnualEntity> {

    /**
     * Igual que en TransaccionProcessor: la data legacy mezcla 4 formatos de
     * fecha (uuuu-MM-dd, uuuu/MM/dd, dd-MM-uuuu, dd/MM/uuuu) en el mismo
     * archivo. Se usa el patron 'u' (año ISO) y no 'y' (año-de-era): con
     * ResolverStyle.STRICT, 'y' exige ademas una Era que nunca llega y la
     * fecha nunca resuelve. 'u' evita ese problema y, junto con STRICT,
     * sigue rechazando fechas inexistentes (mes 13, dia 32, etc.).
     */
    private static final List<DateTimeFormatter> FORMATOS_FECHA = List.of(
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("uuuu/MM/dd").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd-MM-uuuu").withResolverStyle(ResolverStyle.STRICT),
            DateTimeFormatter.ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT));

    // Tipos de movimiento validos segun el sistema legacy del Banco XYZ.
    // La data trae variantes invalidas (ej. "depósito" con tilde, "pago")
    // que deben quedar RECHAZADAS en vez de contaminar el resumen anual.
    private static final Set<String> TIPOS_VALIDOS = Set.of("deposito", "retiro", "compra");

    @Override
    public CuentaAnualEntity process(CuentaAnualDTO dto) {
        Long cuentaId = parsearIdSeguro(dto.getCuentaId());
        String tipoTransaccion = dto.getTransaccion() == null ? "" : dto.getTransaccion().trim().toLowerCase();
        String descripcion = dto.getDescripcion() == null ? "" : dto.getDescripcion().trim();

        // 1) Validar fecha (legacy puede traer varios formatos)
        LocalDate fecha = parsearFecha(dto.getFecha());
        if (fecha == null) {
            return rechazar(cuentaId, null, tipoTransaccion, null, descripcion,
                    "Formato de fecha invalido: '" + dto.getFecha() + "'");
        }

        // 2) Validar tipo de movimiento (rechaza variantes como "depósito" o "pago")
        if (!TIPOS_VALIDOS.contains(tipoTransaccion)) {
            return rechazar(cuentaId, fecha, tipoTransaccion, null, descripcion,
                    "Tipo de transaccion invalido: '" + dto.getTransaccion() + "'");
        }

        // 3) Validar descripcion faltante
        if (descripcion.isBlank()) {
            return rechazar(cuentaId, fecha, tipoTransaccion, null, descripcion, "Descripcion faltante");
        }

        // 4) Validar monto
        BigDecimal monto;
        try {
            monto = new BigDecimal(dto.getMonto().trim());
        } catch (Exception ex) {
            return rechazar(cuentaId, fecha, tipoTransaccion, null, descripcion, "Monto no numerico: '" + dto.getMonto() + "'");
        }
        if (monto.compareTo(BigDecimal.ZERO) == 0) {
            return rechazar(cuentaId, fecha, tipoTransaccion, monto, descripcion, "Monto en cero");
        }
        // Un deposito/ingreso siempre debe ser positivo; un retiro/compra es negativo por naturaleza.
        boolean esIngreso = tipoTransaccion.equals("deposito");
        if (esIngreso && monto.compareTo(BigDecimal.ZERO) < 0) {
            return rechazar(cuentaId, fecha, tipoTransaccion, monto, descripcion,
                    "Monto invalido: un deposito no puede ser negativo");
        }

        return CuentaAnualEntity.builder()
                .cuentaId(cuentaId)
                .fecha(fecha)
                .tipoTransaccion(tipoTransaccion)
                .monto(monto)
                .descripcion(descripcion)
                .estado("VALIDA")
                .motivoRechazo(null)
                .build();
    }

    private CuentaAnualEntity rechazar(Long cuentaId, LocalDate fecha, String tipoTransaccion,
                                        BigDecimal monto, String descripcion, String motivo) {
        return CuentaAnualEntity.builder()
                .cuentaId(cuentaId)
                .fecha(fecha)
                .tipoTransaccion(tipoTransaccion)
                .monto(monto)
                .descripcion(descripcion)
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
