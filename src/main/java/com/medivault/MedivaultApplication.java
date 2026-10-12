package com.medivault;

import java.util.Arrays;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.medivault.document.crypto.DocumentKeyTool;

@SpringBootApplication
public class MedivaultApplication {

	public static void main(String[] args) {
		// "document-key ..." manages the key file offline, without starting Spring or touching any database.
		if (args.length > 0 && DocumentKeyTool.COMMAND.equals(args[0])) {
			System.exit(DocumentKeyTool.run(Arrays.copyOfRange(args, 1, args.length)));
		}
		SpringApplication.run(MedivaultApplication.class, args);
	}

}
