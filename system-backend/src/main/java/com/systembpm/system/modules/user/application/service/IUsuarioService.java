package com.systembpm.system.modules.user.application.service;

import com.systembpm.system.modules.user.application.dto.UsuarioCreateDto;
import com.systembpm.system.modules.user.application.dto.UsuarioResponseDto;
import com.systembpm.system.modules.user.application.dto.UsuarioUpdateDto;
import com.systembpm.system.modules.user.domain.Usuario;

import java.util.List;
import java.util.Optional;

/**
 * Interfaz del servicio de usuarios.
 * Define el contrato de operaciones para la gestión de usuarios.
 */
public interface IUsuarioService {

    /**
     * Registra un nuevo usuario en el sistema.
     * 
     * @param dto Datos del usuario a crear
     * @return El usuario creado (sin contraseña)
     */
    UsuarioResponseDto registrarUsuario(UsuarioCreateDto dto);

    /**
     * Obtiene todos los usuarios del sistema.
     * 
     * @return Lista de usuarios
     */
    List<UsuarioResponseDto> listarUsuarios();

    /**
     * Obtiene un usuario por su ID.
     * 
     * @param id El ID del usuario
     * @return El usuario si existe
     */
    Optional<UsuarioResponseDto> obtenerUsuarioPorId(String id);

    /**
     * Actualiza los datos de un usuario existente.
     * 
     * @param id  El ID del usuario a actualizar
     * @param dto Los nuevos datos del usuario
     * @return El usuario actualizado
     */
    Optional<UsuarioResponseDto> actualizarUsuario(String id, UsuarioUpdateDto dto);

    /**
     * Elimina un usuario del sistema (soft delete o hard delete).
     * 
     * @param id El ID del usuario a eliminar
     * @return true si se eliminó, false si no existía
     */
    boolean eliminarUsuario(String id);

    /**
     * Busca un usuario por su email.
     * 
     * @param email El email del usuario
     * @return El usuario si existe
     */
    Optional<Usuario> buscarPorEmail(String email);
}
