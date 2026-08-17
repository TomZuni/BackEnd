package cl.duoc.bankxyz.migracion.entities;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Entity de salida: una cuenta con su interes mensual ya calculado y su
 * saldo final actualizado, tal como se persiste en interes_procesado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InteresEntity {

    private Long cuentaId;
    private String nombre;
    private String tipoCuenta;
    private Integer edad;

    private BigDecimal saldoOriginal;
    private BigDecimal tasaInteres;
    private BigDecimal interesCalculado;
    private BigDecimal saldoFinal;

    private String estado;         // VALIDA / RECHAZADA
    private String motivoRechazo;
}
