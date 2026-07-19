package com.transportista.service;

import com.transportista.dto.InscripcionRequest;
import com.transportista.dto.InscripcionResponse;
import com.transportista.entity.Curso;
import com.transportista.entity.Inscripcion;
import com.transportista.enums.EstadoInscripcion;
import com.transportista.rabbitmq.RabbitMQConfig;
import com.transportista.repository.CursoRepository;
import com.transportista.repository.InscripcionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InscripcionService {

    private final InscripcionRepository inscripcionRepository;
    private final CursoRepository cursoRepository;
    private final RabbitTemplate rabbitTemplate;

    public InscripcionResponse inscribirEstudiante(InscripcionRequest request) {
        log.info("Inscribiendo estudiante {} al curso {}", request.getEstudiante(), request.getCodigoCurso());

        Curso curso = cursoRepository.findByCodigoCurso(request.getCodigoCurso())
                .orElseThrow(() -> new RuntimeException("Curso no encontrado con codigo: " + request.getCodigoCurso()));

        Inscripcion inscripcion = Inscripcion.builder()
                .codigoCurso(request.getCodigoCurso())
                .estudiante(request.getEstudiante())
                .emailEstudiante(request.getEmailEstudiante())
                .estado(EstadoInscripcion.PENDIENTE)
                .build();

        Inscripcion inscripcionGuardada = inscripcionRepository.save(inscripcion);
        log.info("Inscripcion guardada con id: {}", inscripcionGuardada.getId());

        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY_PRINCIPAL, inscripcionGuardada);
        log.info("Inscripcion {} enviada a cola principal", inscripcionGuardada.getId());

        return toResponse(inscripcionGuardada);
    }

    @Transactional(readOnly = true)
    public List<InscripcionResponse> consultarInscripciones(String estudiante) {
        log.info("Consultando inscripciones. Estudiante: {}", estudiante);

        List<Inscripcion> inscripciones;
        if (estudiante != null && !estudiante.isEmpty()) {
            inscripciones = inscripcionRepository.findByEstudiante(estudiante);
        } else {
            inscripciones = inscripcionRepository.findAll();
        }

        return inscripciones.stream().map(this::toResponse).collect(Collectors.toList());
    }

    public InscripcionResponse calificarInscripcion(Long id, Double calificacion) {
        log.info("Calificando inscripcion id {} con calificacion {}", id, calificacion);

        Inscripcion inscripcion = inscripcionRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Inscripcion no encontrada con id: " + id));

        inscripcion.setCalificacion(calificacion);
        inscripcion.setEstado(EstadoInscripcion.CALIFICADO);

        Inscripcion updated = inscripcionRepository.save(inscripcion);
        log.info("Inscripcion {} calificada", id);

        return toResponse(updated);
    }

    @Transactional(readOnly = true)
    public List<InscripcionResponse> procesarColaYGuardar() {
        log.info("Procesando inscripciones en estado INSCRITO");

        List<Inscripcion> inscripciones = inscripcionRepository.findByEstado(EstadoInscripcion.INSCRITO);

        return inscripciones.stream().map(this::toResponse).collect(Collectors.toList());
    }

    private InscripcionResponse toResponse(Inscripcion inscripcion) {
        return InscripcionResponse.builder()
                .id(inscripcion.getId())
                .codigoCurso(inscripcion.getCodigoCurso())
                .estudiante(inscripcion.getEstudiante())
                .emailEstudiante(inscripcion.getEmailEstudiante())
                .fechaInscripcion(inscripcion.getFechaInscripcion())
                .calificacion(inscripcion.getCalificacion())
                .estado(inscripcion.getEstado().name())
                .fechaProcesamiento(inscripcion.getFechaProcesamiento())
                .build();
    }
}
