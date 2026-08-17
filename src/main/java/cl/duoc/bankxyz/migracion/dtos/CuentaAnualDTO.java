package cl.duoc.bankxyz.migracion.dtos;

import lombok.Data;

/**
 * DTO de entrada: representa una linea cruda de cuentas_anuales.csv (el
 * historial de movimientos de una cuenta durante el ano).
 */
@Data
public class CuentaAnualDTO {

    private String cuentaId;
    private String fecha;
    private String transaccion; // deposito, retiro, compra, etc.
    private String monto;
    private String descripcion;
}
