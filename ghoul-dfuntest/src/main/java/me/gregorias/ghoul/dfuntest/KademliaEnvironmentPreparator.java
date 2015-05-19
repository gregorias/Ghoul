package me.gregorias.ghoul.dfuntest;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedList;
import java.util.NoSuchElementException;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import me.gregorias.ghoul.interfaces.Main;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import me.gregorias.dfuntest.Environment;
import me.gregorias.dfuntest.EnvironmentPreparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Preparator of testing environments for Kademlia tests.
 *
 * This preparator prepares XML configuration file expected by
 * {@link me.gregorias.ghoul.interfaces.Main} and sets up environment properties for
 * {@link KademliaAppFactory}.
 *
 * It assumes that:
 * <ul>
 * <li> All required dependency libraries are in lib/ directory. </li>
 * <li> Kademlia is in kademlia.jar file. </li>
 * </ul>
 *
 * @author Grzegorz Milka
 */
public class KademliaEnvironmentPreparator implements EnvironmentPreparator<Environment> {
  public static final String INITIAL_PORT_ARGUMENT_NAME =
      "KademliaEnvironmentPreparator.initialPort";
  public static final String INITIAL_REST_PORT_ARGUMENT_NAME =
      "KademliaEnvironmentPreparator.initialRestPort";
  public static final String BUCKET_SIZE_ARGUMENT_NAME =
      "KademliaEnvironmentPreparator.bucketSize";
  public static final String HEART_BEAT_DELAY_ARGUMENT_NAME =
      "KademliaEnvironmentPreparator.heartBeatDelay";
  public static final String USE_DIFFERENT_PORTS_ARGUMENT_NAME =
      "KademliaEnvironmentPreparator.useDifferentPorts";

  private static final String XML_CONFIG_FILENAME = "kademlia.xml";
  private static final String LOCAL_CONFIG_PATH = XML_CONFIG_FILENAME;
  private static final Path LOCAL_JAR_PATH = FileSystems.getDefault().getPath("kademlia.jar");
  private static final Path LOCAL_LIBS_PATH = FileSystems.getDefault().getPath("lib");
  private static final Logger LOGGER = LoggerFactory.getLogger(KademliaEnvironmentPreparator.class);
  private final int mInitialPort;
  private final int mInitialRestPort;
  private final int mBucketSize;
  private final long mHeartBeatDelay;
  private final boolean mUseDifferentPorts;

  /**
   * @param initialPort Port number used by first kademlia application for kademlia.
   * @param initialRestPort Port number used by first kademlia application for REST.
   * @param bucketSize BucketSize of each kademlia peer.
   * @param heartBeatDelay heart beat delay of each kademlia peer.
   * @param useDifferentPorts Whether each kademlia peer should use a different port.
   * Used when all peers are on the same host.
   */
  @Inject
  public KademliaEnvironmentPreparator(@Named(INITIAL_PORT_ARGUMENT_NAME) int initialPort,
      @Named(INITIAL_REST_PORT_ARGUMENT_NAME) int initialRestPort,
      @Named(BUCKET_SIZE_ARGUMENT_NAME) int bucketSize,
      @Named(HEART_BEAT_DELAY_ARGUMENT_NAME) long heartBeatDelay,
      @Named(USE_DIFFERENT_PORTS_ARGUMENT_NAME) boolean useDifferentPorts) {
    mInitialPort = initialPort;
    mInitialRestPort = initialRestPort;
    mBucketSize = bucketSize;
    mHeartBeatDelay = heartBeatDelay;
    mUseDifferentPorts = useDifferentPorts;
  }

  @Override
  public void cleanAll(Collection<Environment> envs) {
    String logFilePath = getLogFilePath();
    for (Environment env : envs) {
      try {
        env.removeFile(LOCAL_CONFIG_PATH);
        env.removeFile(logFilePath);
      } catch (IOException e) {
        LOGGER.error("clean(): Could not clean environment.", e);
      } catch (InterruptedException e) {
        LOGGER.warn("clean(): Could not clean environment.", e);
        Thread.currentThread().interrupt();
      }
    }

  }

  @Override
  public void cleanOutput(Collection<Environment> envs) {
    String logFilePath = getLogFilePath();
    for (Environment env : envs) {
      try {
        env.removeFile(logFilePath);
      } catch (IOException e) {
        LOGGER.error("clean(): Could not clean environment.", e);
      } catch (InterruptedException e) {
        LOGGER.warn("clean(): Could not clean environment.", e);
        Thread.currentThread().interrupt();
      }
    }
  }

  @Override
  public void collectOutput(Collection<Environment> envs, Path destPath) {
    String logFilePath = getLogFilePath();
    for (Environment env : envs) {
      try {
        env.copyFilesToLocalDisk(logFilePath, destPath.resolve(env.getId() + ""));
      } catch (IOException e) {
        LOGGER.warn("collectOutputAndLogFiles(): Could not copy log file.", e);
      }
    }
  }

  /**
   * This method expects that environments are numbered from 0.
   * Zero environment will be configured as kademlia bootstrap server.
   */
  @Override
  public void prepare(Collection<Environment> envs) throws IOException {
    Collection<Environment> preparedEnvs = new LinkedList<>();
    LOGGER.info("prepare()");
    Environment zeroEnvironment = findZeroEnvironment(envs);
    for (Environment env : envs) {
      XMLConfiguration xmlConfig = prepareXMLAndEnvConfiguration(env, zeroEnvironment);
      try {
        xmlConfig.save(XML_CONFIG_FILENAME);
        String targetPath = ".";
        Path localConfigPath = FileSystems.getDefault().getPath(LOCAL_CONFIG_PATH).toAbsolutePath();
        env.copyFilesFromLocalDisk(localConfigPath, targetPath);
        env.copyFilesFromLocalDisk(LOCAL_JAR_PATH.toAbsolutePath(), targetPath);
        env.copyFilesFromLocalDisk(LOCAL_LIBS_PATH.toAbsolutePath(), targetPath);
        preparedEnvs.add(env);
      } catch (ConfigurationException | IOException e) {
        cleanAll(preparedEnvs);
        LOGGER.error("prepare() -> Could not prepare environment.", e);
        throw new IOException(e);
      }
    }
  }

  @Override
  public void restore(Collection<Environment> envs) throws IOException {
    LOGGER.info("restore()");
    Environment zeroEnvironment = findZeroEnvironment(envs);
    String logFilePath = getLogFilePath();
    for (Environment env : envs) {
      prepareXMLAndEnvConfiguration(env, zeroEnvironment);
      try {
        env.removeFile(logFilePath);
      } catch (InterruptedException e) {
        LOGGER.info("restore() -> restore was interrupted. Leaving method.");
        Thread.currentThread().interrupt();
        return;
      }
    }
  }

  private Environment findZeroEnvironment(Collection<Environment> envs) throws
      NoSuchElementException {
    for (Environment env : envs) {
      if (env.getId() == 0) {
        return env;
      }
    }
    throw new NoSuchElementException("No zero environment present");
  }

  private String getLogFilePath() {
    return "./" + KademliaApp.LOG_FILE;
  }

  private XMLConfiguration prepareXMLAndEnvConfiguration(Environment env, Environment zeroEnv) {
    XMLConfiguration xmlConfiguration = new XMLConfiguration();
    int portSkew = mUseDifferentPorts ? env.getId() : 0;
    xmlConfiguration.addProperty(Main.XML_FIELD_LOCAL_ADDRESS, env.getHostname());
    xmlConfiguration.addProperty(Main.XML_FIELD_LOCAL_PORT, mInitialPort + portSkew);
    xmlConfiguration.addProperty(Main.XML_FIELD_LOCAL_KEY, env.getId());
    xmlConfiguration.addProperty(Main.XML_FIELD_BUCKET_SIZE, mBucketSize);
    xmlConfiguration.addProperty(Main.XML_FIELD_BOOTSTRAP_KEY, 0);
    xmlConfiguration.addProperty(Main.XML_FIELD_BOOTSTRAP_ADDRESS, zeroEnv.getHostname());
    xmlConfiguration.addProperty(Main.XML_FIELD_BOOTSTRAP_PORT, mInitialPort);
    xmlConfiguration.addProperty(Main.XML_FIELD_REST_PORT, mInitialRestPort + portSkew);
    xmlConfiguration.addProperty(Main.XML_FIELD_HEART_BEAT_DELAY, mHeartBeatDelay);
    xmlConfiguration.addProperty(Main.XML_FIELD_IS_REGISTRAR, false);
    env.setProperty(Main.XML_FIELD_LOCAL_ADDRESS, env.getHostname());
    env.setProperty(Main.XML_FIELD_REST_PORT, mInitialRestPort + portSkew);
    return xmlConfiguration;
  }

}
