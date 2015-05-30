package me.gregorias.ghoul.interfaces;

import me.gregorias.ghoul.security.CryptographyTools;
import me.gregorias.ghoul.security.GhoulProtocolException;
import me.gregorias.ghoul.security.KeyGenerator;
import me.gregorias.ghoul.security.RegistrarClient;
import me.gregorias.ghoul.security.RegistrarDescription;
import me.gregorias.ghoul.security.SignedCertificate;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.security.KeyPair;
import java.util.Collection;

/**
 * Created by grzesiek on 27.05.15.
 */
public class RegTest {
  private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

  public static void main(String[] args) {
    if (args.length < 1) {
      System.out.println("Usage: Main CONFIG_FILE");
      return;
    }
    LOGGER.info("main({})", args[0]);
    String configFile = args[0];
    XMLConfiguration config;
    try {
      config = new XMLConfiguration(new File(configFile));
    } catch (ConfigurationException e) {
      LOGGER.error("main() -> Could not read configuration due to ConfigurationException.", e);
      return;
    }

    KeyPair localKeyPair = KeyGenerator.generateKeys();
    Collection<RegistrarDescription> registrars;

    try {
      registrars = RegistrarMain.loadRegistrarDescriptions(
          config.configurationAt(RegistrarMain.XML_FIELD_REGISTRARS_INFO));
      if (registrars.size() == 0) {
        LOGGER.error("main(): Could not load registrars information.");
        return;
      }
    } catch (ClassNotFoundException | IOException e) {
      LOGGER.error("main() -> Could not load registrars description.", e);
      return;
    }

    for (int i = 0; i < 1000; i++) {
      long begTime = System.nanoTime();
      try {
        Collection<SignedCertificate> certificate = joinDHT(
            registrars,
            localKeyPair,
            config);
        if (certificate.size() == 0) {
          LOGGER.error("main(): Could not get certificate.");
        }
        LOGGER.info("main(): Joining took {} nanoseconds.", System.nanoTime() - begTime);
      } catch (ClassNotFoundException | GhoulProtocolException | IOException e) {
        LOGGER.error("main(): Could not join the DHT due to an exception.", e);
      }
    }
    LOGGER.info("main() -> void");
  }

  private static Collection<SignedCertificate> joinDHT(
      Collection<RegistrarDescription> registrars,
      KeyPair keyPair,
      HierarchicalConfiguration config)
      throws IOException, ClassNotFoundException, GhoulProtocolException {
    RegistrarClient client = new RegistrarClient(
        registrars,
        keyPair,
        CryptographyTools.getDefault());
    LOGGER.info("joinDHT(): Joining the DHT.");
    return client.joinDHT();
  }
}
