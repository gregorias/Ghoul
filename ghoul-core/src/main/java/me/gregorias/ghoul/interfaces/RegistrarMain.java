package me.gregorias.ghoul.interfaces;

import me.gregorias.ghoul.kademlia.data.Key;
import me.gregorias.ghoul.security.CryptographyTools;
import me.gregorias.ghoul.security.Registrar;
import me.gregorias.ghoul.security.RegistrarDescription;
import me.gregorias.ghoul.security.RegistrarMessageSender;
import me.gregorias.ghoul.security.RegistrarMessageSenderImpl;
import me.gregorias.ghoul.utils.Utils;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Signature;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Created by grzesiek on 19.05.15.
 */
public class RegistrarMain {
  public static final String XML_FIELD_IS_REGISTRAR = "isRegistrar";
  public static final String XML_FIELD_REGISTRAR_KEY = "registrarKey";
  public static final String XML_FIELD_REGISTRAR_PUB_KEY_FILE = "registrarPubKeyFile";
  public static final String XML_FIELD_REGISTRAR_PRIV_KEY_FILE = "registrarPrivKeyFile";
  public static final String XML_FIELD_REGISTRAR_ADDRESS = "registrarAddress";
  public static final String XML_FIELD_REGISTRAR_PORT = "registrarPort";
  public static final String XML_FIELD_REGISTRARS_INFO = "registrarsInfo";
  public static final String XML_FIELD_REGISTRAR_INFO = "registrarInfo";

  private static final Logger LOGGER = LoggerFactory.getLogger(RegistrarMain.class);

  public static void main(String[] args) {
    LOGGER.info("main(): Starting the program.");

    if (args.length < 1) {
      System.out.println("Usage: Main CONFIG_FILE");
      return;
    }

    String configFile = args[0];
    XMLConfiguration config;
    try {
      config = new XMLConfiguration(new File(configFile));
    } catch (ConfigurationException e) {
      LOGGER.error("main() -> Could not read the configuration file properly.", e);
      return;
    }

    Optional<Registrar> registrarOptional = RegistrarMain.createRegistrar(config);

    if (registrarOptional.isPresent()) {
      LOGGER.debug("main(): Running the registrar.");
      registrarOptional.get().run();
    }

    LOGGER.info("main() -> Ending the program.");
  }

  public static Collection<RegistrarDescription> loadRegistrarDescriptions(
      HierarchicalConfiguration configuration) throws IOException, ClassNotFoundException {
    Collection<RegistrarDescription> descriptions = new ArrayList<>();
    for (HierarchicalConfiguration sub :
        configuration.configurationsAt(XML_FIELD_REGISTRAR_INFO)) {
      Key key = new Key(sub.getInt(XML_FIELD_REGISTRAR_KEY));
      PublicKey pubKey = (PublicKey) Utils.loadObjectFromFile(
          sub.getString(XML_FIELD_REGISTRAR_PUB_KEY_FILE));
      String address = sub.getString(XML_FIELD_REGISTRAR_ADDRESS);
      int port = sub.getInt(XML_FIELD_REGISTRAR_PORT);

      descriptions.add(new RegistrarDescription(pubKey, key, new InetSocketAddress(address, port)));
    }
    return descriptions;
  }

  private static Optional<Registrar> createRegistrar(XMLConfiguration config) {
    try {
      if (!config.getBoolean(XML_FIELD_IS_REGISTRAR)) {
        return Optional.empty();
      }

      final PublicKey pubKey = (PublicKey) Utils.loadObjectFromFile(
          config.getString(XML_FIELD_REGISTRAR_PUB_KEY_FILE));
      final PrivateKey privKey = (PrivateKey) Utils.loadObjectFromFile(
          config.getString(XML_FIELD_REGISTRAR_PRIV_KEY_FILE));
      final Key registrarKey = new Key(config.getInt(XML_FIELD_REGISTRAR_KEY));
      final int port = config.getInt(XML_FIELD_REGISTRAR_PORT);

      KeyPair keyPair = new KeyPair(pubKey, privKey);

      final RegistrarDescription description =
          new RegistrarDescription(pubKey, registrarKey, new InetSocketAddress(port));

      final Collection<RegistrarDescription> allRegistrars =
          loadRegistrarDescriptions(config.configurationAt(XML_FIELD_REGISTRARS_INFO));

      RegistrarMessageSender sender = new RegistrarMessageSenderImpl(allRegistrars);
      ScheduledExecutorService executor = Executors.newScheduledThreadPool(4);
      CryptographyTools tools = new CryptographyTools(Signature.getInstance("Sha256WithDSA"),
          MessageDigest.getInstance("SHA-256"),
          new SecureRandom());

      return Optional.of(new Registrar(description,
          keyPair,
          allRegistrars,
          sender,
          executor,
          tools,
          port));
    } catch (ClassNotFoundException | IOException | NoSuchAlgorithmException e) {
      return Optional.empty();
    }
  }
}
