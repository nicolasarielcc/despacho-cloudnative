package com.transportista.repository;

import com.transportista.entity.GuiaDespachoProcesada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repositorio JPA para GuiaDespachoProcesada.
 * Tabla NUEVA, distinta a la usada en sumativas anteriores.
 * Almacena guías que fueron consumidas exitosamente desde la cola 1.
 */
@Repository
public interface GuiaDespachoProcesadaRepository extends JpaRepository<GuiaDespachoProcesada, Long> {

    /**
     * Busca procesadas por código de guía original.
     */
    List<GuiaDespachoProcesada> findByCodigoGuia(String codigoGuia);
}
