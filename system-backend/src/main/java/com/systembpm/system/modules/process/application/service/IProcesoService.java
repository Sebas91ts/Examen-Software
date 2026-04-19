package com.systembpm.system.modules.process.application.service;

import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.domain.Proceso;

/**
 * Contrato del servicio de procesos BPMN.
 */
public interface IProcesoService {

    Proceso guardar(ProcesoCreateDto dto);
}
