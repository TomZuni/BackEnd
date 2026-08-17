package cl.duoc.bankxyz.migracion.dtos;

import lombok.Data;

/**
 * DTO de entrada: representa una linea cruda de transacciones.csv, tal como
 * llega desde el FlatFileItemReader, antes de cualquier validacion.
 *
 * Los campos se leen como String (no como LocalDate/BigDecimal) a proposito:
 * los datos legacy pueden traer formatos de fecha invalidos o valores vacios,
 * y queremos que esos casos lleguen intactos al ItemProcessor para poder
 * detectarlos y rechazarlos ahi, en lugar de que el Job falle en el reader.
 */
@Data
public class TransaccionDTO {

    private String id;
    private String fecha;
    private String monto;
    private String tipo;
}
