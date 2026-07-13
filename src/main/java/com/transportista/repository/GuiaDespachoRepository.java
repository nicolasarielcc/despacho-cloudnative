package com.transportista.repository;

import com.transportista.entity.GuiaDespacho;
import com.transportista.enums.EstadoGuia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para la entidad GuiaDespacho.
 * Proporciona métodos de consulta personalizados además de los CRUD básicos.
 */
@Repository
public interface GuiaDespachoRepository extends JpaRepository<GuiaDespacho, Long> {

    /**
     * Busca guías por transportista y rango de fechas.
     * Requerimiento de la pauta: "Consultar guías por transportista y fecha"
     */
    @Query("SELECT g FROM GuiaDespacho g " +
           "WHERE g.transportista = :transportista " +
           "AND g.fechaEmision BETWEEN :fechaInicio AND :fechaFin " +
           "ORDER BY g.fechaEmision DESC")
    List<GuiaDespacho> findByTransportistaAndFechaEmisionBetween(
            @Param("transportista") String transportista,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin);

    /**
     * Busca una guía por su código único.
     */
    Optional<GuiaDespacho> findByCodigoGuia(String codigoGuia);

    /**
     * Busca todas las guías de un estado específico.
     */
    List<GuiaDespacho> findByEstado(EstadoGuia estado);

    /**
     * Verifica si existe una guía con un código específico.
     */
    boolean existsByCodigoGuia(String codigoGuia);
}
