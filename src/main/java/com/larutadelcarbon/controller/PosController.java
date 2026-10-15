package com.larutadelcarbon.controller;

import com.larutadelcarbon.model.Categoria;
import com.larutadelcarbon.model.Producto;
import com.larutadelcarbon.model.Venta;
import com.larutadelcarbon.repository.CategoriaRepository;
import com.larutadelcarbon.repository.ProductoRepository;
import com.larutadelcarbon.service.ProductoService;
import com.larutadelcarbon.service.VentaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
public class PosController {

    private final ProductoService productoService;
    private final VentaService ventaService;
    private final CategoriaRepository categoriaRepository;
    private final ProductoRepository productoRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping({"/", "/carta"})
    public String carta(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "pos";
    }

    @GetMapping("/index")
    public String index() {
        return "index";
    }

    @GetMapping("/inventario")
    public String inventario(Model model) {
        model.addAttribute("productos", productoService.listarTodos());
        model.addAttribute("categorias", categoriaRepository.findAll());
        return "inventario";
    }

    @PostMapping("/inventario/categoria")
    public String guardarCategoria(@RequestParam String nombre) {
        if (nombre != null && !nombre.trim().isEmpty()) {
            Categoria cat = new Categoria();
            cat.setNombre(nombre.trim());
            categoriaRepository.save(cat);
        }
        return "redirect:/inventario";
    }

    @PostMapping("/inventario/categoria/eliminar/{id}")
    @Transactional
    public String eliminarCategoria(@PathVariable Long id,
                                    @RequestParam(name = "claveConfirmacion", required = false, defaultValue = "") String claveConfirmacion) {

        Categoria catObj = categoriaRepository.findById(id).orElse(null);
        if (catObj != null && catObj.getNombre().toLowerCase().contains("hamburguesa personalizada")) {
            System.err.println("Operación denegada: Intento de eliminar categoría protegida del sistema.");
            return "redirect:/inventario";
        }

        if ("confirmo".equalsIgnoreCase(claveConfirmacion.trim())) {
            try {
                Categoria catGeneral = categoriaRepository.findAll().stream()
                        .filter(c -> "Sin Categoría".equalsIgnoreCase(c.getNombre()) && !c.getId().equals(id))
                        .findFirst()
                        .orElseGet(() -> {
                            Categoria c = new Categoria();
                            c.setNombre("Sin Categoría");
                            return categoriaRepository.save(c);
                        });

                List<Producto> productos = productoRepository.findAll();
                for (Producto p : productos) {
                    if (p.getCategoria() != null && p.getCategoria().getId().equals(id)) {
                        p.setCategoria(catGeneral);
                        productoRepository.save(p);
                    }
                }
                productoRepository.flush();

                categoriaRepository.deleteById(id);
                categoriaRepository.flush();

            } catch (Exception e) {
                System.err.println("Advertencia al eliminar categoría: " + e.getMessage());
            }
        }
        return "redirect:/inventario";
    }

    @PostMapping("/inventario/producto")
    public String guardarProducto(@RequestParam String nombre,
                                  @RequestParam Double precio,
                                  @RequestParam Integer stock,
                                  @RequestParam Long categoriaId) {
        Producto p = new Producto();
        p.setNombre(nombre);
        p.setPrecio(BigDecimal.valueOf(precio));
        p.setStock(stock);
        categoriaRepository.findById(categoriaId).ifPresent(p::setCategoria);
        productoService.guardar(p);
        return "redirect:/inventario";
    }

    @PostMapping("/inventario/producto/actualizar-stock")
    @ResponseBody
    public ResponseEntity<?> actualizarStock(@RequestParam Long id, @RequestParam Integer stock) {
        return productoRepository.findById(id).map(producto -> {
            producto.setStock(stock);
            productoRepository.save(producto);
            return ResponseEntity.ok().body(Map.of("status", "success", "stock", stock));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/inventario/producto/eliminar/{id}")
    @Transactional
    public String eliminarProducto(@PathVariable Long id) {
        try {
            entityManager.clear();
            entityManager.createNativeQuery("DELETE FROM productos WHERE id = :prodId")
                    .setParameter("prodId", id)
                    .executeUpdate();
        } catch (Exception e) {
            entityManager.createNativeQuery("UPDATE productos SET stock = 0 WHERE id = :prodId")
                    .setParameter("prodId", id)
                    .executeUpdate();
        }
        return "redirect:/inventario";
    }

    @GetMapping("/ventas")
    public String registroVentas(Model model) {
        try {
            List<Venta> ventas = ventaService.listarTodas();
            if (ventas != null) {
                ventas.forEach(v -> {
                    if (v.getTotal() == null) v.setTotal(BigDecimal.ZERO);
                    if (v.getNombreCliente() == null) v.setNombreCliente("Público General");
                });
                model.addAttribute("ventas", ventas);
            } else {
                model.addAttribute("ventas", Collections.emptyList());
            }
        } catch (Exception e) {
            System.err.println("Error al cargar ventas: " + e.getMessage());
            model.addAttribute("ventas", Collections.emptyList());
        }
        return "registro_ventas";
    }

    @GetMapping("/api/ventas/{id}")
    @ResponseBody
    public ResponseEntity<?> obtenerDetalleVenta(@PathVariable Long id) {
        return ventaService.buscarPorId(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/api/ventas/procesar")
    @ResponseBody
    public ResponseEntity<?> procesarVenta(@RequestBody Venta venta) {
        try {
            Venta ventaGuardada = ventaService.registrarVenta(venta);
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Venta registrada con exito",
                    "numeroComprobante", ventaGuardada.getNumeroComprobante()
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        }
    }
}