package com.dreambake.controller;

import com.dreambake.entity.Coupon;
import com.dreambake.repository.CouponRepository;
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

    @GetMapping("/validate/{code}")
    public ResponseEntity<Coupon> validateCoupon(@PathVariable String code) {
        Optional<Coupon> optionalCoupon = couponRepository.findByCodeIgnoreCase(code);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            if (coupon.isActive() && (coupon.isUnlimited() || (coupon.getUsageLimit() != null && coupon.getUsageLimit() > 0))) {
                return ResponseEntity.ok(coupon);
            }
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostMapping("/consume/{code}")
    public ResponseEntity<Void> consumeCoupon(@PathVariable String code) {
        Optional<Coupon> optionalCoupon = couponRepository.findByCodeIgnoreCase(code);
        if (optionalCoupon.isPresent()) {
            Coupon coupon = optionalCoupon.get();
            if (coupon.isActive() && (coupon.isUnlimited() || (coupon.getUsageLimit() != null && coupon.getUsageLimit() > 0))) {
                if (!coupon.isUnlimited()) {
                    coupon.setUsageLimit(coupon.getUsageLimit() - 1);
                    if (coupon.getUsageLimit() == 0) {
                        coupon.setActive(false);
                    }
                    couponRepository.save(coupon);
                }
                return ResponseEntity.ok().build();
            }
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.notFound().build();
    }
}
