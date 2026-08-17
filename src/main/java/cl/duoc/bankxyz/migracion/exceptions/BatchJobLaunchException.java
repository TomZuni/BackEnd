package cl.duoc.bankxyz.migracion.exceptions;

import lombok.experimental.StandardException;

/**
 * Excepcion de negocio que envuelve los errores "checked" que puede lanzar
 * el JobLauncher al intentar iniciar cualquiera de los 3 Jobs (por ejemplo,
 * una ejecucion ya en curso, o JobParameters invalidos/repetidos). Las capas
 * de servicio la atrapan y la relanzan como excepcion unchecked, para que
 * los controllers REST no necesiten conocer las excepciones internas de
 * Spring Batch.
 */
@StandardException
public class BatchJobLaunchException extends RuntimeException {

}
