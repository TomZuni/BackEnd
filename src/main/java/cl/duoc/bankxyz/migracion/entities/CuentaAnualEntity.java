package cl.duoc.bankxyz.migracion.entities;

import java.math.BigDecimal;
import java.time.LocalDate;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity de salida: un movimiento anual de una cuenta ya validado, tal como
 * se persiste en movimiento_anual_procesado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuentaAnualEntity {

    private Long cuentaId;
    private LocalDate fecha;
    private String tipoTransaccion;
    private BigDecimal monto;
    private String descripcion;

    private String estado;         // VALIDA / RECHAZADA
    private String motivoRechazo;
}
