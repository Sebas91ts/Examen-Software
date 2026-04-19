package com.systembpm.system.modules.process.application.service;

import com.systembpm.system.modules.process.application.dto.ProcesoCreateDto;
import com.systembpm.system.modules.process.domain.Proceso;
import com.systembpm.system.modules.process.infrastructure.repository.ProcesoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Implementacion del servicio de procesos BPMN.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProcesoServiceImpl implements IProcesoService {

    private final ProcesoRepository procesoRepository;

    @Override
    public Proceso guardar(ProcesoCreateDto dto) {
        log.info("Guardando proceso BPMN con nombre: {}", dto.getNombre());

        validarDto(dto);
        String nombreNormalizado = normalizarNombre(dto.getNombre());

        boolean existe = procesoRepository
                .findByNombreIgnoreCase(nombreNormalizado)
                .isPresent();

        if (existe) {
             throw new IllegalArgumentException("Ya existe un proceso con ese nombre");
        }

        Proceso proceso = Proceso.builder()
                .nombre(nombreNormalizado)
                .xml(dto.getXml().trim())
                .version(1)
                .createdAt(LocalDateTime.now())
                .build();

        Proceso procesoGuardado = procesoRepository.save(proceso);
        log.info("Proceso BPMN guardado exitosamente con ID: {}", procesoGuardado.getId());

        return procesoGuardado;
    }

    @Override
    public List<Proceso> listar() {
        log.info("Listando procesos BPMN");
        return procesoRepository.findAll();
    }

    @Override
    public Proceso obtenerPorId(String id) {
        log.info("Buscando proceso BPMN por ID: {}", id);

        return procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));
    }

    @Override
    public Proceso actualizar(String id, ProcesoCreateDto dto) {
        log.info("Actualizando proceso BPMN con ID: {}", id);

        validarDto(dto);

        Proceso procesoExistente = procesoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Proceso no encontrado con ID: " + id));

        procesoExistente.setNombre(dto.getNombre().trim());
        procesoExistente.setXml(dto.getXml().trim());

        Proceso procesoActualizado = procesoRepository.save(procesoExistente);
        log.info("Proceso BPMN actualizado exitosamente con ID: {}", procesoActualizado.getId());

        return procesoActualizado;
    }

    private void validarDto(ProcesoCreateDto dto) {
        if (dto == null) {
            throw new IllegalArgumentException("Los datos del proceso son obligatorios");
        }

        if (dto.getNombre() == null || dto.getNombre().isBlank()) {
            throw new IllegalArgumentException("El nombre del proceso es obligatorio");
        }

        if (dto.getXml() == null || dto.getXml().isBlank()) {
            throw new IllegalArgumentException("El XML BPMN no puede estar vacio");
        }

        if (!dto.getXml().contains("<bpmn:")) {
            throw new IllegalArgumentException("El XML proporcionado no parece ser un BPMN valido");
        }
    }

    private String normalizarNombre(String nombre) {
        return nombre.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
