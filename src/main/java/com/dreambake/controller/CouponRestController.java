package com.dreambake.controller;

import com.dreambake.entity.Coupon;
import com.dreambake.repository.CouponRepository;
import com.dreambake.service.CouponService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/coupons")
public class CouponRestController {

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponService couponService;

    @GetMapping
    public List<Coupon> getAllCoupons() {
        return couponRepository.findAll();
    }

    @PostMapping
    public Coupon createCoupon(@RequestBody Coupon coupon) {
        return couponRepository.save(coupon);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Coupon> updateCoupon(@PathVariable Long id, @RequestBody Coupon couponDetails) {
        Optional<Coupon> optionalCoupon = couponRepository.findById(id);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            coupon.setCode(couponDetails.getCode());
            coupon.setType(couponDetails.getType());
            coupon.setActive(couponDetails.isActive());
            coupon.setDescription(couponDetails.getDescription());
            coupon.setDiscountPercentage(couponDetails.getDiscountPercentage());
            coupon.setCustomText(couponDetails.getCustomText());
            coupon.setUsageLimit(couponDetails.getUsageLimit());
            coupon.setUnlimited(couponDetails.isUnlimited());
            return ResponseEntity.ok(couponRepository.save(coupon));
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCoupon(@PathVariable Long id) {
        if (couponRepository.existsById(id)) {
            couponRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    // Lo usa el carrito de la tienda (público) para mostrar el descuento antes de enviar el pedido.
    // Solo consulta: el uso del cupón se descuenta en el servidor al guardar el pedido
    // (OrderRestController), así nadie puede gastar los usos de un código desde afuera.
    @GetMapping("/validate/{code}")
    public ResponseEntity<Coupon> validateCoupon(@PathVariable String code) {
        if (couponRepository.findByCodeIgnoreCase(code).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return couponService.findUsable(code)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.badRequest().build());
    }
}
