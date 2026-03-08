
package com.example.hacktj2026;

import org.springframework.web.bind.annotation.*;
import java.util.Map;


@RestController
public class HelioController {

    private final HelioService helioService;

    public HelioController(HelioService helioService) {
        this.helioService = helioService;
    }

    @GetMapping("/analyze")
    public Map<String,String> analyze() {

        return helioService.analyzeCalendar();

    }
}
