package com.dreambake.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class BakerController {

    @GetMapping("/baker")
    public String bakerDashboard() {
        return "baker";
    }
}
