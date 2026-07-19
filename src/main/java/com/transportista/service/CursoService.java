package com.transportista.service;

import com.transportista.dto.CursoRequest;
import com.transportista.dto.CursoResponse;
import com.transportista.entity.Curso;
import com.transportista.enums.EstadoCurso;
import com.transportista.rabbitmq.CursoProducer;
import com.transportista.repository.CursoRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CursoService {

    private final CursoRepository cursoRepository;
    private final CursoProducer cursoProducer;

    private static final DateTimeFormatter CODIGO_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmm");

    @Transactional
    public CursoResponse crearCurso(CursoRequest request) {
        log.info("Creando nuevo curso: {}", request.getNombre());

        Curso curso = Curso.builder()
                .codigoCurso(generarCodigoCurso())
                .nombre(request.getNombre())
                .instructor(request.getInstructor())
                .descripcion(request.getDescripcion())
                .creditos(request.getCreditos())
                .fechaInicio(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .estado(EstadoCurso.PENDIENTE)
                .build();

        Curso saved = cursoRepository.save(curso);
        log.info("Curso guardado con id: {} y codigo: {}", saved.getId(), saved.getCodigoCurso());

        cursoProducer.enviarAColaPrincipal(saved);
        log.info("Curso {} enviado a cola principal", saved.getCodigoCurso());

        return toResponse(saved);
    }

    @Transactional
    public CursoResponse modificarCurso(Long id, CursoRequest request) {
        log.info("Modificando curso con id: {}", id);

        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con id: " + id));

        curso.setNombre(request.getNombre());
        curso.setInstructor(request.getInstructor());
        curso.setDescripcion(request.getDescripcion());
        curso.setCreditos(request.getCreditos());
        curso.setFechaInicio(request.getFechaInicio());
        curso.setFechaFin(request.getFechaFin());
        curso.setEstado(EstadoCurso.PENDIENTE);

        Curso updated = cursoRepository.save(curso);

        cursoProducer.enviarAColaPrincipal(updated);
        log.info("Curso {} reenviado a cola principal", updated.getCodigoCurso());

        return toResponse(updated);
    }

    @Transactional
    public void eliminarCurso(Long id) {
        log.info("Eliminando curso con id: {}", id);
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con id: " + id));
        cursoRepository.delete(curso);
        log.info("Curso {} eliminado correctamente", id);
    }

    @Transactional(readOnly = true)
    public List<CursoResponse> consultarCursos(String instructor, String fechaStr) {
        log.info("Consultando cursos. Instructor: {}, Fecha: {}", instructor, fechaStr);

        List<Curso> cursos;

        if (instructor != null && fechaStr != null) {
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            cursos = cursoRepository.findByInstructorAndFechaInicioBetween(
                    instructor, fechaInicio, fechaFin);
        } else if (instructor != null) {
            cursos = cursoRepository.findAll().stream()
                    .filter(c -> c.getInstructor().equalsIgnoreCase(instructor))
                    .collect(Collectors.toList());
        } else if (fechaStr != null) {
            LocalDateTime fechaInicio = LocalDateTime.parse(fechaStr + "T00:00:00");
            LocalDateTime fechaFin = LocalDateTime.parse(fechaStr + "T23:59:59");
            cursos = cursoRepository.findAll().stream()
                    .filter(c -> !c.getFechaInicio().isBefore(fechaInicio)
                            && !c.getFechaInicio().isAfter(fechaFin))
                    .collect(Collectors.toList());
        } else {
            cursos = cursoRepository.findAll();
        }

        return cursos.stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public CursoResponse subirCursoAS3(Long id) {
        log.info("Solicitando subida a S3 para curso id {}", id);
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con id: " + id));

        cursoProducer.enviarAColaPrincipal(curso);
        log.info("Curso {} reenviado a cola principal para procesar S3", curso.getCodigoCurso());

        return toResponse(curso);
    }

    @Transactional(readOnly = true)
    public CursoResponse descargarCurso(Long id) {
        log.info("Descargando curso con id: {}", id);
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con id: " + id));
        return toResponse(curso);
    }

    public CursoResponse consumirMensajeDeCola() {
        log.info("Consumiendo mensaje directamente de la cola principal (HTTP pull)");
        Curso curso = cursoProducer.consumirDeColaPrincipal();
        if (curso == null) {
            return null;
        }
        return toResponse(curso);
    }

    @Transactional(readOnly = true)
    public CursoResponse obtenerPorId(Long id) {
        Curso curso = cursoRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con id: " + id));
        return toResponse(curso);
    }

    private String generarCodigoCurso() {
        String timestamp = LocalDateTime.now().format(CODIGO_FORMATTER);
        String secuencial = String.format("%03d", (int) (Math.random() * 1000));
        return "CUR-" + timestamp + "-" + secuencial;
    }

    private CursoResponse toResponse(Curso curso) {
        return CursoResponse.builder()
                .id(curso.getId())
                .codigoCurso(curso.getCodigoCurso())
                .nombre(curso.getNombre())
                .instructor(curso.getInstructor())
                .descripcion(curso.getDescripcion())
                .creditos(curso.getCreditos())
                .fechaInicio(curso.getFechaInicio())
                .fechaFin(curso.getFechaFin())
                .estado(curso.getEstado().name())
                .urlS3(curso.getUrlS3())
                .fechaCreacion(curso.getFechaCreacion())
                .build();
    }
}
