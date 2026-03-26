package com.project.transportation_model.controller;

import com.project.transportation_model.model.TransportationRequest;
import com.project.transportation_model.model.TransportationResponse;
import com.project.transportation_model.service.TransportationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/transportation")
@CrossOrigin(origins = "*") // This is CRUCIAL so your React GUI can talk to Java
public class TransportationController {

    @Autowired
    private TransportationService service;

    /**
     * TEST ENDPOINT
     * Check if the backend is alive: http://localhost:8080/api/transportation/test
     */
    @GetMapping("/test")
    public String test() {
        return "The Receptionist is at her desk! Backend is ready.";
    }

    /**
     * MAIN CALCULATION ENDPOINT
     * Handles North-West, Least Cost, and Vogel's based on user choice
     */
    @PostMapping("/calculate")
    public TransportationResponse calculate(@RequestBody TransportationRequest request) {
        double[][] allocation;
        List<String> steps = new ArrayList<>();
        String method = (request.getMethod() != null) ? request.getMethod().toUpperCase() : "NW";

        if (method.equals("VOGEL")) {
            allocation = service.calculateVogels(request.getSuppliers(), request.getCustomers(),
                    request.getCosts(), request.getSupply(), request.getDemand(), steps);
        } else if (method.contains("LEAST")) {
            allocation = service.calculateLeastCost(request.getSuppliers(), request.getCustomers(),
                    request.getCosts(), request.getSupply(), request.getDemand());
        } else {
            allocation = service.calculateNorthWest(request.getSuppliers(), request.getCustomers(),
                    request.getSupply(), request.getDemand());
        }

        // Pass the original costs. The service will handle the size difference.
        double finalCost = service.calculateTotalCost(allocation, request.getCosts());

        return new TransportationResponse(allocation, finalCost, steps);
    }
}