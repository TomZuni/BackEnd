package cl.duoc.bankxyz.migracion.processors;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

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

    private static final DateTimeFormatter FORMATO_ISO = DateTimeFormatter.ISO_LOCAL_DATE; // yyyy-MM-dd
    private static final DateTimeFormatter FORMATO_LEGACY = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    @Override
    public CuentaAnualEntity process(CuentaAnualDTO dto) {
        Long cuentaId = parsearIdSeguro(dto.getCuentaId());
        String tipoTransaccion = dto.getTransaccion() == null ? "" : dto.getTransaccion().trim().toLowerCase();
        String descripcion = dto.getDescripcion() == null ? "" : dto.getDescripcion().trim();

        // 1) Validar fecha (legacy puede traer yyyy/MM/dd)
        LocalDate fecha = parsearFecha(dto.getFecha());
        if (fecha == null) {
            return rechazar(cuentaId, null, tipoTransaccion, null, descripcion,
                    "Formato de fecha invalido: '" + dto.getFecha() + "'");
        }

        // 2) Validar descripcion faltante
        if (descripcion.isBlank()) {
            return rechazar(cuentaId, fecha, tipoTransaccion, null, descripcion, "Descripcion faltante");
        }

        // 3) Validar monto
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
        try {
            return LocalDate.parse(valor, FORMATO_ISO);
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(valor, FORMATO_LEGACY);
            } catch (DateTimeParseException ex2) {
                return null;
            }
        }
    }
}
