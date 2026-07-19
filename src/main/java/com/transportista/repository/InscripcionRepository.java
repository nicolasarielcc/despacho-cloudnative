package com.transportista.repository;

import com.transportista.entity.Inscripcion;
import com.transportista.enums.EstadoInscripcion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface InscripcionRepository extends JpaRepository<Inscripcion, Long> {

    List<Inscripcion> findByCodigoCurso(String codigoCurso);

    List<Inscripcion> findByEstudiante(String estudiante);

    List<Inscripcion> findByEstado(EstadoInscripcion estado);
}
