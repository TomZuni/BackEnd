package cl.duoc.bankxyz.migracion.dtos;

import lombok.Data;

/**
 * DTO de entrada: representa una linea cruda de intereses.csv (datos de
 * cuentas de ahorro/prestamo/hipoteca antes de calcular el interes mensual).
 */
@Data
public class InteresDTO {

    private String cuentaId;
    private String nombre;
    private String saldo;
    private String edad;
    private String tipo; // ahorro, prestamo, hipoteca
}
