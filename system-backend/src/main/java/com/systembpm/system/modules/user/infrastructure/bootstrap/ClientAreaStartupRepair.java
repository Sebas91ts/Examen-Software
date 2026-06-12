package com.systembpm.system.modules.user.infrastructure.bootstrap;

import com.systembpm.system.modules.area.domain.Area;
import com.systembpm.system.modules.area.infrastructure.repository.AreaRepository;
import com.systembpm.system.modules.user.domain.Usuario;
import com.systembpm.system.modules.user.infrastructure.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Reparacion idempotente para usuarios cliente creados antes de usar tenantId como area.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ClientAreaStartupRepair implements ApplicationRunner {

    private static final String CLIENT_AREA_NAME = "Cliente";

    private final UsuarioRepository usuarioRepository;
    private final AreaRepository areaRepository;

    @Override
    public void run(ApplicationArguments args) {
        Optional<Area> clientArea = areaRepository.findByNombreIgnoreCase(CLIENT_AREA_NAME)
                .filter(area -> !Boolean.FALSE.equals(area.getActiva()));

        if (clientArea.isEmpty()) {
            log.warn("client-area.repair.skipped reason=client-area-not-found areaName={}", CLIENT_AREA_NAME);
            return;
        }

        Area defaultClientArea = clientArea.get();
        List<Usuario> clients = usuarioRepository.findAll().stream()
                .filter(this::isClient)
                .toList();

        int updated = 0;
        for (Usuario client : clients) {
            if (repairClientArea(client, defaultClientArea)) {
                usuarioRepository.save(client);
                updated++;
                log.info("client-area.repair.updated userId={} email={} areaId={} tenantId={}",
                        client.getId(), client.getEmail(), client.getAreaId(), client.getTenantId());
            }
        }

        log.info("client-area.repair.completed scanned={} updated={} defaultAreaId={} defaultAreaName={}",
                clients.size(), updated, defaultClientArea.getId(), defaultClientArea.getNombre());
    }

    private boolean repairClientArea(Usuario client, Area defaultClientArea) {
        boolean changed = false;

        if (isBlank(client.getAreaId())) {
            client.setAreaId(defaultClientArea.getId());
            client.setAreaNombre(defaultClientArea.getNombre());
            changed = true;
        } else if (isBlank(client.getAreaNombre())) {
            String areaName = areaRepository.findById(client.getAreaId())
                    .map(Area::getNombre)
                    .orElse(defaultClientArea.getNombre());
            client.setAreaNombre(areaName);
            changed = true;
        }

        if (isBlank(client.getTenantId())) {
            client.setTenantId(isBlank(client.getAreaId()) ? defaultClientArea.getId() : client.getAreaId());
            changed = true;
        }

        return changed;
    }

    private boolean isClient(Usuario usuario) {
        if (usuario == null || usuario.getRoles() == null) {
            return false;
        }

        return usuario.getRoles().stream()
                .filter(role -> role != null && !role.isBlank())
                .map(String::trim)
                .anyMatch(role -> "ROLE_CLIENT".equalsIgnoreCase(role) || "CLIENT".equalsIgnoreCase(role));
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
