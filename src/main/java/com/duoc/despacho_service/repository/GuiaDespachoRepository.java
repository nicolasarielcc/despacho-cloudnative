package com.duoc.despacho_service.repository;

import com.duoc.despacho_service.entity.GuiaDespacho;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface GuiaDespachoRepository extends JpaRepository<GuiaDespacho, Long> {

    java.util.Optional<GuiaDespacho> findByNumeroGuia(String numeroGuia);
    List<GuiaDespacho> findByTransportista(String transportista);
    List<GuiaDespacho> findByFecha(LocalDate fecha);
    List<GuiaDespacho> findByTransportistaAndFecha(String transportista, LocalDate fecha);
}