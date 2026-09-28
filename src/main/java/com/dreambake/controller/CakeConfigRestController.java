package com.dreambake.controller;

import com.dreambake.entity.CakeOption;
import com.dreambake.entity.CakeSize;
import com.dreambake.repository.CakeOptionRepository;
import com.dreambake.repository.CakeSizeRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/cake-config")
public class CakeConfigRestController {

    @Autowired
    private CakeSizeRepository cakeSizeRepository;

    @Autowired
    private CakeOptionRepository cakeOptionRepository;

    @PostConstruct
    public void initDefaultData() {
        if (cakeSizeRepository.count() == 0) {
            cakeSizeRepository.saveAll(List.of(
                    new CakeSize("10 porciones (14x12cm)", 44000, 2, false, true),
                    new CakeSize("15 porciones (14x15cm)", 60000, 2, false, true),
                    new CakeSize("20 porciones (16x15cm)", 82000, 3, false, true),
                    new CakeSize("30 porciones (18x15cm)", 125000, 3, false, true),
                    new CakeSize("30 porciones (2 Pisos)", 135000, 3, true, true),
                    new CakeSize("40-50 porciones (2 Pisos)", 176000, 3, true, true)));
        }

        if (cakeOptionRepository.count() == 0) {
            List<String> sponges = List.of("Vainilla", "Chocolate", "Limón", "Coco");
            for (String s : sponges) {
                cakeOptionRepository.save(new CakeOption(s, "Sponge", true));
            }

            List<String> cremaFills = List.of("Dulce de leche", "Chantilly", "Crema oreo", "Crema bonobon",
                    "Crema bariloche", "Mousse de frutilla", "Ganache blanco", "Ganache frutos rojos");
            for (String f : cremaFills) {
                cakeOptionRepository.save(new CakeOption(f, "Crema", true));
            }

            List<String> fondantFills = List.of("Dulce de leche", "Crema Bariloche", "Ganache blanco/negro",
                    "Ganache frutos rojos", "Ganache dulce de leche", "Ganache frutillas", "Ganache chocooreo",
                    "Ganache pistacho", "Ganache menta", "Ganache maracuyá", "Ganache banana");
            for (String f : fondantFills) {
                cakeOptionRepository.save(new CakeOption(f, "Fondant", true));
            }
        }
    }

    @GetMapping("/public")
    public Map<String, Object> getPublicConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("sizes", cakeSizeRepository.findByEnabledTrueOrderByIdAsc());
        response.put("options", cakeOptionRepository.findByEnabledTrueOrderByIdAsc());
        return response;
    }

    @GetMapping("/admin")
    public Map<String, Object> getAdminConfig() {
        Map<String, Object> response = new HashMap<>();
        response.put("sizes", cakeSizeRepository.findAllByOrderByIdAsc());
        response.put("options", cakeOptionRepository.findAllByOrderByCategoryAscIdAsc());
        return response;
    }

    @PostMapping("/sizes")
    public ResponseEntity<CakeSize> createSize(@RequestBody CakeSize size) {
        size.setId(null);
        if (size.getEnabled() == null)
            size.setEnabled(true);
        return ResponseEntity.ok(cakeSizeRepository.save(size));
    }

    @PutMapping("/sizes/{id}")
    public ResponseEntity<CakeSize> updateSize(@PathVariable Long id, @RequestBody CakeSize updated) {
        return cakeSizeRepository.findById(id).map(size -> {
            size.setName(updated.getName());
            size.setPrice(updated.getPrice());
            size.setMaxRellenos(updated.getMaxRellenos());
            size.setMixSponge(updated.getMixSponge());
            size.setEnabled(updated.getEnabled());
            return ResponseEntity.ok(cakeSizeRepository.save(size));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/sizes/{id}")
    public ResponseEntity<Void> deleteSize(@PathVariable Long id) {
        cakeSizeRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/options")
    public ResponseEntity<CakeOption> createOption(@RequestBody CakeOption option) {
        option.setId(null);
        if (option.getEnabled() == null)
            option.setEnabled(true);
        return ResponseEntity.ok(cakeOptionRepository.save(option));
    }

    @PutMapping("/options/{id}")
    public ResponseEntity<CakeOption> updateOption(@PathVariable Long id, @RequestBody CakeOption updated) {
        return cakeOptionRepository.findById(id).map(opt -> {
            opt.setName(updated.getName());
            opt.setCategory(updated.getCategory());
            opt.setEnabled(updated.getEnabled());
            return ResponseEntity.ok(cakeOptionRepository.save(opt));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/options/{id}")
    public ResponseEntity<Void> deleteOption(@PathVariable Long id) {
        cakeOptionRepository.deleteById(id);
        return ResponseEntity.ok().build();
    }
}
