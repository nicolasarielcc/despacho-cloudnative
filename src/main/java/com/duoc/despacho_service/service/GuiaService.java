package com.duoc.despacho_service.service;

import com.duoc.despacho_service.dto.request.GuiaRequestDTO;
import com.duoc.despacho_service.dto.response.GuiaResponseDTO;

import java.time.LocalDate;
import java.util.List;

public interface GuiaService {

    GuiaResponseDTO crearGuiaTemporal(GuiaRequestDTO guiaRequestDTO);

    GuiaResponseDTO subirAS3(Long id);

    byte[] descargarGuiaConPermisos(Long id, String rol);

    GuiaResponseDTO actualizarGuia(Long id, GuiaRequestDTO guiaRequestDTO);

    void eliminarGuia(Long id);

    List<GuiaResponseDTO> buscarPorTransportistaYFecha(String transportista, LocalDate fecha);
}
