package com.larutadelcarbon;

import com.larutadelcarbon.model.Categoria;
import com.larutadelcarbon.repository.CategoriaRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class LarutadelcarbonApplication {

	public static void main(String[] args) {
		SpringApplication.run(LarutadelcarbonApplication.class, args);
	}

	@Bean
	public CommandLineRunner initData(CategoriaRepository categoriaRepository) {
		return args -> {
			String nombreCategoriaTarget = "Ingrediente para hamburguesa personalizada";

			if (categoriaRepository.findByNombre(nombreCategoriaTarget).isEmpty()) {
				Categoria cat = new Categoria();
				cat.setNombre(nombreCategoriaTarget);
				categoriaRepository.save(cat);
				System.out.println("✅ Categoría creada automáticamente: " + nombreCategoriaTarget);
			}
		};
	}

}