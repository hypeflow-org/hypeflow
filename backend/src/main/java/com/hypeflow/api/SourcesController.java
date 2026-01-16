package com.hypeflow.api;

import com.hypeflow.model.SourceDescriptor;
import com.hypeflow.service.SourceRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SourcesController {

    private final SourceRegistry sourceRegistry;

    public SourcesController(SourceRegistry sourceRegistry) {
        this.sourceRegistry = sourceRegistry;
    }

    @GetMapping("/sources")
    public List<SourceDescriptor> getSources() {
        return sourceRegistry.list();
    }
}
