package com.transportista.repository;

import com.transportista.entity.Curso;
import com.transportista.enums.EstadoCurso;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface CursoRepository extends JpaRepository<Curso, Long> {

    @Query("SELECT c FROM Curso c " +
           "WHERE c.instructor = :instructor " +
           "AND c.fechaInicio BETWEEN :fechaInicio AND :fechaFin " +
           "ORDER BY c.fechaInicio DESC")
    List<Curso> findByInstructorAndFechaInicioBetween(
            @Param("instructor") String instructor,
            @Param("fechaInicio") LocalDateTime fechaInicio,
            @Param("fechaFin") LocalDateTime fechaFin);

    Optional<Curso> findByCodigoCurso(String codigoCurso);

    List<Curso> findByEstado(EstadoCurso estado);

    boolean existsByCodigoCurso(String codigoCurso);
}
