package com.dreambake;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class DreambakeApplication {

	public static void main(String[] args) {
		// Hora de Argentina para TODA la app, sin importar la zona del servidor.
		// Los cumpleaños, los backups y los recordatorios dependen de "qué día es hoy".
		// Se hace antes de arrancar Spring para que nada use otra zona por error.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Argentina/Buenos_Aires"));
		SpringApplication.run(DreambakeApplication.class, args);
	}

}
