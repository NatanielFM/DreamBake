package com.dreambake.service;

import com.dreambake.entity.Coupon;
import com.dreambake.repository.CouponRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Reglas de los cupones en un solo lugar: cuándo se pueden usar y cómo se descuenta un uso.
@Service
public class CouponService {

    @Autowired
    private CouponRepository couponRepository;

    // Devuelve el cupón solo si existe, está activo y le quedan usos (o es ilimitado)
    public Optional<Coupon> findUsable(String code) {
        if (code == null || code.isBlank()) return Optional.empty();
        return couponRepository.findByCodeIgnoreCase(code.trim())
                .filter(c -> c.isActive() && (c.isUnlimited() || (c.getUsageLimit() != null && c.getUsageLimit() > 0)));
    }

    // Descuenta un uso. Cuando llega a 0 el cupón queda inactivo.
    // Lo llama el servidor al guardar un pedido de la tienda (antes lo hacía el navegador
    // con un endpoint público, y cualquiera podía gastar los usos de un código).
    // Devuelve false si el cupón se agotó justo en ese momento (otro cliente usó el último uso).
    // La resta la hace la base en una sola consulta (ver CouponRepository.consumeOneUse): antes se
    // leía el número, se restaba en Java y se guardaba, y dos pedidos simultáneos podían leer
    // "1 uso" los dos y obtener el descuento los dos.
    @Transactional
    public boolean consume(Coupon coupon) {
        if (coupon.isUnlimited()) return true;
        return couponRepository.consumeOneUse(coupon.getId()) == 1;
    }
}
