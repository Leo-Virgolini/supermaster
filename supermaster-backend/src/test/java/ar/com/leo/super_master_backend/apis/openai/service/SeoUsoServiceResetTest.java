package ar.com.leo.super_master_backend.apis.openai.service;

import ar.com.leo.super_master_backend.apis.openai.repository.SeoUsoRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeoUsoServiceResetTest {

    @Mock SeoUsoRepository repository;
    @InjectMocks SeoUsoService service;

    @Test
    void reset_llamaAlRepositorio() {
        when(repository.reset()).thenReturn(1);
        service.reset();
        verify(repository).reset();
    }
}
