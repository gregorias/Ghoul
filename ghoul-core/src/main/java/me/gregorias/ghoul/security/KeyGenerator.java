package me.gregorias.ghoul.security;

import me.gregorias.ghoul.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;

public class KeyGenerator {
  private static final Logger LOGGER = LoggerFactory.getLogger(KeyGenerator.class);
  public static void main(String[] args) {
    LOGGER.info("main({}): Starting the program.");

    if (args.length < 1) {
      System.out.println("Usage: Main OUTPUT_FILE_NAME");
      return;
    }

    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("DSA");
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      Utils.storeObjectToFile(args[0] + ".pub", keyPair.getPublic());
      Utils.storeObjectToFile(args[0] + ".priv", keyPair.getPrivate());
    } catch (NoSuchAlgorithmException | IOException e) {
      LOGGER.error("main()", e);
    }
    LOGGER.info("main({}): Ending the program.");

  }
}
