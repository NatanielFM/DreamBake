package com.dreambake.controller;

import com.dreambake.entity.Customer;
import com.dreambake.entity.CustomerTag;
import com.dreambake.repository.CustomerRepository;
import com.dreambake.repository.CustomerTagRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api")
public class CustomerRestController {

    @Autowired
    private CustomerRepository customerRepo;

    @Autowired
    private CustomerTagRepository tagRepo;

    // ================= CLIENTES =================

    @GetMapping("/customers")
    public List<Customer> getAllCustomers() {
        return customerRepo.findAll();
    }

    @PostMapping("/customers")
    public Customer createCustomer(@Valid @RequestBody CustomerRequest request) {
        return saveOrUpdateCustomer(new Customer(), request);
    }

    @PutMapping("/customers/{id}")
    public ResponseEntity<Customer> updateCustomer(@PathVariable Long id, @Valid @RequestBody CustomerRequest request) {
        return customerRepo.findById(id)
                .map(customer -> ResponseEntity.ok(saveOrUpdateCustomer(customer, request)))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/customers/{id}")
    public ResponseEntity<Void> deleteCustomer(@PathVariable Long id) {
        if (customerRepo.existsById(id)) {
            customerRepo.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    private Customer saveOrUpdateCustomer(Customer customer, CustomerRequest req) {
        customer.setName(req.name());
        customer.setEmail(req.email());
        customer.setPhone(req.phone());
        customer.setAddress(req.address());

        CustomerTag tag = tagRepo.findByName(req.tagName())
                .orElseGet(() -> tagRepo.findByName("Sin etiqueta").orElse(null));
        customer.setTag(tag);

        return customerRepo.save(customer);
    }

    public record CustomerRequest(
            @NotBlank(message = "El nombre del cliente es obligatorio.") String name,
            String email, String phone, String address, String tagName) {
    }

    // ================= ETIQUETAS (TAGS) =================

    @GetMapping("/tags")
    public List<CustomerTag> getAllTags() {
        return tagRepo.findAll(Sort.by(Sort.Direction.ASC, "displayOrder"));
    }

    @PostMapping("/tags")
    public ResponseEntity<?> createTag(@RequestBody CustomerTag tag) {
        if (tagRepo.findByName(tag.getName()).isPresent()) {
            return ResponseEntity.badRequest().body("La etiqueta ya existe.");
        }
        long count = tagRepo.count();
        tag.setDisplayOrder((int) count);
        return ResponseEntity.ok(tagRepo.save(tag));
    }

    @PutMapping("/tags/{id}")
    public ResponseEntity<CustomerTag> updateTag(@PathVariable Long id, @RequestBody CustomerTag request) {
        return tagRepo.findById(id).map(tag -> {
            tag.setName(request.getName());
            return ResponseEntity.ok(tagRepo.save(tag));
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/tags/{id}")
    public ResponseEntity<?> deleteTag(@PathVariable Long id) {
        return tagRepo.findById(id).map(tag -> {
            if (!tag.isDeletable()) {
                return ResponseEntity.badRequest().body("No se puede eliminar esta etiqueta por defecto.");
            }
            CustomerTag defaultTag = tagRepo.findByName("Sin etiqueta").orElse(null);
            List<Customer> customers = customerRepo.findByTag(tag);
            for (Customer c : customers) {
                c.setTag(defaultTag);
                customerRepo.save(c);
            }
            tagRepo.delete(tag);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/tags/reorder")
    public ResponseEntity<?> reorderTags(@RequestBody List<Long> orderedIds) {
        for (int i = 0; i < orderedIds.size(); i++) {
            Long id = orderedIds.get(i);
            Optional<CustomerTag> optTag = tagRepo.findById(id);
            if (optTag.isPresent()) {
                CustomerTag tag = optTag.get();
                tag.setDisplayOrder(i);
                tagRepo.save(tag);
            }
        }
        return ResponseEntity.ok().build();
    }
}