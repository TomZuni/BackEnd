package cl.duoc.bankxyz.migracion.controllers;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import cl.duoc.bankxyz.migracion.services.ConsultaResultadosService;
import lombok.RequiredArgsConstructor;

/**
 * Endpoints de solo lectura (GET) para revisar en Postman lo que cada Job
 * dejo en la base de datos H2, luego de dispararlos con los controllers de
 * "/procesar".
 */
@RestController
@RequestMapping("/api/resultados")
@RequiredArgsConstructor
public class ConsultaResultadosController {

    private final ConsultaResultadosService consultaResultadosService;

    @GetMapping("/transacciones")
    public List<Map<String, Object>> transacciones() {
        return consultaResultadosService.transaccionesProcesadas();
    }

    @GetMapping("/transacciones/resumen")
    public List<Map<String, Object>> resumenTransacciones() {
        return consultaResultadosService.resumenTransaccionesDiarias();
    }

    @GetMapping("/intereses")
    public List<Map<String, Object>> intereses() {
        return consultaResultadosService.interesesProcesados();
    }

    @GetMapping("/cuentas-anuales")
    public List<Map<String, Object>> cuentasAnuales() {
        return consultaResultadosService.movimientosAnualesProcesados();
    }

    @GetMapping("/cuentas-anuales/resumen")
    public List<Map<String, Object>> resumenCuentasAnuales() {
        return consultaResultadosService.resumenAnualPorCuenta();
    }
}
