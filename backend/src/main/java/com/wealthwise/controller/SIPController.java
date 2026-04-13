package com.wealthwise.controller;

import com.wealthwise.dto.*;
import com.wealthwise.service.SIPService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/sip")
public class SIPController {

    @Autowired
    private SIPService sipService;

    @GetMapping("/dashboard")
    public SIPDashboardResponse getDashboard() {
        return sipService.getDashboard();
    }

    @GetMapping("/compare")
    public SIPComparisonResponse compare() {
        return sipService.compare();
    }

    @GetMapping("/optimize")
    public String optimize() {
        return sipService.optimize();
    }

    @GetMapping("/topup")
    public TopUpResponse topup() {
        return sipService.calculateTopUp();
    }
}