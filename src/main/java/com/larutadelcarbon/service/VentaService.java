package com.larutadelcarbon.service;

import com.larutadelcarbon.model.DetalleVenta;
import com.larutadelcarbon.model.Producto;
import com.larutadelcarbon.model.Venta;
import com.larutadelcarbon.repository.ProductoRepository;
import com.larutadelcarbon.repository.VentaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;

    public List<Venta> listarTodas() {
        return ventaRepository.findAll();
    }

    public Optional<Venta> buscarPorId(Long id) {
        return ventaRepository.findById(id);
    }

    @Transactional
    public Venta registrarVenta(Venta venta) {
        // 1. Validar reglas de Boleta / Factura
        validarComprobante(venta);

        // 2. Generar correlativo automático
        String ultimoNumero = ventaRepository.findTopByTipoComprobanteOrderByNumeroComprobanteDesc(venta.getTipoComprobante())
                .map(Venta::getNumeroComprobante)
                .orElse(null);

        venta.setNumeroComprobante(generarSiguienteCorrelativo(venta.getTipoComprobante(), ultimoNumero));
        venta.setFecha(LocalDateTime.now());

        // 3. Procesar detalles y actualizar stock
        BigDecimal totalCalculado = BigDecimal.ZERO;

        for (DetalleVenta detalle : venta.getDetalles()) {
            Producto producto = productoRepository.findById(detalle.getProducto().getId())
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + detalle.getProducto().getId()));

            if (producto.getStock() < detalle.getCantidad()) {
                throw new RuntimeException("Stock insuficiente para el producto: " + producto.getNombre());
            }

            // Descontar stock
            producto.setStock(producto.getStock() - detalle.getCantidad());
            productoRepository.save(producto);

            // Asignar datos al detalle
            detalle.setVenta(venta);
            detalle.setPrecioUnitario(producto.getPrecio());
            BigDecimal subtotal = producto.getPrecio().multiply(BigDecimal.valueOf(detalle.getCantidad()));
            detalle.setSubtotal(subtotal);

            totalCalculado = totalCalculado.add(subtotal);
        }

        venta.setTotal(totalCalculado);
        return ventaRepository.save(venta);
    }

    private void validarComprobante(Venta venta) {
        if ("FACTURA".equalsIgnoreCase(venta.getTipoComprobante())) {
            if (venta.getDocumentoCliente() == null || venta.getDocumentoCliente().length() != 11) {
                throw new IllegalArgumentException("La Factura requiere un RUC válido de 11 dígitos.");
            }
        } else if ("BOLETA".equalsIgnoreCase(venta.getTipoComprobante())) {
            if (venta.getTotal() != null && venta.getTotal().compareTo(new BigDecimal("70")) > 0) {
                if (venta.getDocumentoCliente() == null || venta.getDocumentoCliente().length() != 8) {
                    throw new IllegalArgumentException("Monto mayor a S/ 70 requiere DNI obligatorio de 8 dígitos.");
                }
            }
        }
    }

    private String generarSiguienteCorrelativo(String tipo, String ultimoCorrelativo) {
        String prefijo = "FACTURA".equalsIgnoreCase(tipo) ? "F001-" : "B001-";
        if (ultimoCorrelativo == null || !ultimoCorrelativo.startsWith(prefijo)) {
            return prefijo + "00000001";
        }
        int numero = Integer.parseInt(ultimoCorrelativo.substring(5)) + 1;
        return prefijo + String.format("%08d", numero);
    }
}