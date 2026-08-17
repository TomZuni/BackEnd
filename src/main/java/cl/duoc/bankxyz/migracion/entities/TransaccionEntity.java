package cl.duoc.bankxyz.migracion.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity de salida: una transaccion diaria ya validada/clasificada por el
 * ItemProcessor, tal como se persiste en transaccion_procesada.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TransaccionEntity {

    private Long transaccionId;
    private LocalDate fecha;
    private BigDecimal monto;
    private String tipo;

    private String estado;         // VALIDA / RECHAZADA
    private String motivoRechazo;  // null si esta VALIDA
}
