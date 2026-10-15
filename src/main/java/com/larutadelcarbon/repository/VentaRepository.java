package com.larutadelcarbon.repository;

import com.larutadelcarbon.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    Optional<Venta> findTopByTipoComprobanteOrderByNumeroComprobanteDesc(String tipoComprobante);
}