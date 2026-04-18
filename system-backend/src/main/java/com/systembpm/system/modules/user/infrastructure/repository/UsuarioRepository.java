package com.systembpm.system.modules.user.infrastructure.repository;

import com.systembpm.system.modules.user.domain.Usuario;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repositorio para acceder a la colección de usuarios en MongoDB.
 * Extiende MongoRepository para obtener operaciones CRUD básicas.
 */
@Repository
public interface UsuarioRepository extends MongoRepository<Usuario, String> {

    /**
     * Busca un usuario por su email.
     * Usado principalmente para autenticación (login).
     * 
     * @param email El email del usuario
     * @return Optional con el usuario si existe, vacío si no
     */
    Optional<Usuario> findByEmail(String email);

    /**
     * Verifica si existe un usuario con el email dado.
     * Usado para validación antes de crear un nuevo usuario.
     * 
     * @param email El email a verificar
     * @return true si existe, false si no
     */
    boolean existsByEmail(String email);
}
