package com.systembpm.system.modules.process.application.service;

import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.domain.Proceso;

import java.util.List;

/**
 * Contrato del servicio de procesos BPMN.
 */
public interface IProcesoService {

    Proceso guardar(ProcesoCreateDto dto);

    List<Proceso> listar();

    Proceso obtenerPorId(String id);

    Proceso actualizar(String id, ProcesoCreateDto dto);

    Proceso publicar(String id);

    Proceso crearNuevaVersion(String id);
}
