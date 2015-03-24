package me.gregorias.ghoul.dfuntest;

import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.Multibinder;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import me.gregorias.dfuntest.ApplicationFactory;
import me.gregorias.dfuntest.Environment;
import me.gregorias.dfuntest.EnvironmentPreparator;
import me.gregorias.dfuntest.LocalEnvironmentFactory;
import me.gregorias.dfuntest.MultiTestRunner;
import me.gregorias.dfuntest.SSHEnvironmentFactory;
import me.gregorias.dfuntest.TestRunner;
import me.gregorias.dfuntest.TestScript;
import me.gregorias.dfuntest.testrunnerbuilders.GuiceTestRunnerModule;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.configuration.XMLConfiguration;
import me.gregorias.dfuntest.EnvironmentFactory;
import me.gregorias.dfuntest.TestResult;
import org.apache.commons.configuration.tree.ConfigurationNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main entry point for {@link KademliaConsistencyCheckTestScript}.
 *
 * It runs dfuntest's TestsScripts for kademlia or displays help message.
 *
 * To run use --env-factory (local|ssh) to specify environment setting and use --config-file PATH
 * and/or --config (KEY=VALUE)* to provide necessary initialization variables.
 *
 * To display help message use --help option.
 *
 * This runner uses a combination of guice injections and configuration argument parsing for
 * initialization. That is argument names of dfuntest classes constructors are annotated with
 * guice @Named decorators. To provide custom value to such argument on runtime you have to
 * either write it into xml config file or provide as a --config command line option.
 *
 * Look at {@link me.gregorias.dfuntest.EnvironmentFactory},
 * {@link me.gregorias.ghoul.dfuntest.KademliaEnvironmentPreparator},
 * and {@link me.gregorias.ghoul.dfuntest.KademliaAppFactory} for required parameters.
 *
 * @author Grzegorz Milka
 */
public class KademliaConsistencyCheckMain {
  private static final Logger LOGGER = LoggerFactory.getLogger(
      KademliaConsistencyCheckMain.class);

  private static final String REPORT_PATH_PREFIX = "report_";

  private static final String CONFIG_OPTION = "config";
  private static final String CONFIG_FILE_OPTION = "config-file";
  private static final String ENV_FACTORY_OPTION = "env-factory";
  private static final String HELP_OPTION = "help";

  private static final Map<String, String> DEFAULT_PROPERTIES = newDefaultProperties();

  private static final String ENV_DIR_PREFIX = "dfuntest";

  private static Class<? extends EnvironmentFactory<Environment>> mEnvironmentFactoryClass;

  public static void main(String[] args) {
    mEnvironmentFactoryClass = null;
    Map<String, String> properties = new HashMap<>(DEFAULT_PROPERTIES);
    try {
      Optional<Map<String, String>> optionalArgumentProperties = parseAndProcessArguments(args);
      if (!optionalArgumentProperties.isPresent()) {
        return;
      } else {
        properties.putAll(optionalArgumentProperties.get());
      }
    } catch (ConfigurationException | IllegalArgumentException | ParseException e) {
      System.exit(1);
      return;
    }

    if (mEnvironmentFactoryClass == null) {
      LOGGER.error("main(): EnvironmentFactory has not been set.");
      System.exit(1);
      return;
    }

    GuiceTestRunnerModule guiceBaseModule = new GuiceTestRunnerModule();
    guiceBaseModule.addProperties(properties);

    KademliaGuiceModule guiceKademliaModule = new KademliaGuiceModule();

    Injector injector = Guice.createInjector(guiceBaseModule, guiceKademliaModule);
    TestRunner testRunner = injector.getInstance(TestRunner.class);

    TestResult result = testRunner.run();

    int status;
    String resultStr;
    if (result.getType() == TestResult.Type.SUCCESS) {
      status = 0;
      resultStr = "successfully";
    } else {
      status = 1;
      resultStr = "with failure";
    }
    LOGGER.info("main(): Test has ended {} with description: {}", resultStr,
        result.getDescription());
    System.exit(status);
  }

  private static class KademliaGuiceModule extends AbstractModule {
    @Override
    protected void configure() {
      bind(new AppFactoryTypeLiteral()).to(KademliaAppFactory.class).in(Singleton.class);
      bind(new EnvironmentPreparatorTypeLiteral()).to(KademliaEnvironmentPreparator.class)
          .in(Singleton.class);
      bind(new EnvironmentFactoryTypeLiteral()).to(mEnvironmentFactoryClass).in(Singleton.class);
      Multibinder<TestScript<KademliaApp>> multiBinder = Multibinder.newSetBinder(binder(),
          new TestScriptTypeLiteral(), Names.named(MultiTestRunner.SCRIPTS_ARGUMENT_NAME));
      //TODO thread pool should be created once.
      multiBinder.addBinding().toInstance(new KademliaConsistencyCheckTestScript(
          Executors.newScheduledThreadPool(100)));
      bind(TestRunner.class).to(new MultiTestRunnerTypeLiteral()).in(Singleton.class);
    }

    @Provides
    @Named(SSHEnvironmentFactory.EXECUTOR_ARGUMENT_NAME)
    @Singleton
    Executor provideSSHEnvironmentFactoryExecutor() {
      return Executors.newCachedThreadPool();
    }


    private static class AppFactoryTypeLiteral
        extends TypeLiteral<ApplicationFactory<Environment, KademliaApp>> {
    }

    private static class EnvironmentFactoryTypeLiteral
        extends TypeLiteral<EnvironmentFactory<Environment>> {
    }

    private static class EnvironmentPreparatorTypeLiteral
        extends TypeLiteral<EnvironmentPreparator<Environment>> {
    }

    private static class TestScriptTypeLiteral extends TypeLiteral<TestScript<KademliaApp>> {
    }
  }

  private static class MultiTestRunnerTypeLiteral extends
      TypeLiteral<MultiTestRunner<Environment, KademliaApp>> {
  }

  private static String calculateCurrentTimeStamp() {
    return new SimpleDateFormat("yyyyMMdd-HHmmssSSS").format(new Date());
  }

  private static Path calculateReportPath() {
    return FileSystems.getDefault().getPath(REPORT_PATH_PREFIX + calculateCurrentTimeStamp());
  }

  /**
   * Transforms {@link org.apache.commons.configuration.HierarchicalConfiguration} into list of
   * key-value pairs.
   *
   * @param config configuration
   * @return list of key-value pairs from configuration
   */
  private static Map<String, String> constructPropertiesFromRootsChildren(
      HierarchicalConfiguration config) {
    Map<String, String> properties = new HashMap<>();
    List<ConfigurationNode> rootsChildrenList = config.getRoot().getChildren();
    for (ConfigurationNode child : rootsChildrenList) {
      HierarchicalConfiguration subConfig = config.configurationAt(child.getName());
      properties.putAll(GuiceTestRunnerModule.configurationToProperties(subConfig));
    }
    return properties;
  }

  private static Options createCLIOptions() {
    Options options = new Options();

    OptionBuilder.withLongOpt(CONFIG_OPTION);
    OptionBuilder.hasArgs();
    OptionBuilder.withValueSeparator(' ');
    OptionBuilder.withDescription("Configure initial dependencies. Arguments should be a list of"
        + " the form: a.b=value1 a.c=value2.");
    Option configOption = OptionBuilder.create();

    OptionBuilder.withLongOpt(CONFIG_FILE_OPTION);
    OptionBuilder.hasArg();
    OptionBuilder.withDescription("XML configuration filename.");
    Option configFileOption = OptionBuilder.create();

    OptionBuilder.withLongOpt(ENV_FACTORY_OPTION);
    OptionBuilder.hasArg();
    OptionBuilder.withDescription("Environment factory name. Can be either local or ssh.");

    Option envFactoryOption = OptionBuilder.create();

    options.addOption(configOption);
    options.addOption(configFileOption);
    options.addOption(envFactoryOption);
    options.addOption("h", HELP_OPTION, false, "Print help.");
    return options;
  }

  private static Map<String, String> newDefaultProperties() {
    Map<String, String> properties = new HashMap<>();
    properties.put(LocalEnvironmentFactory.DIR_PREFIX_ARGUMENT_NAME, ENV_DIR_PREFIX);
    properties.put(SSHEnvironmentFactory.REMOTE_DIR_ARGUMENT_NAME, ENV_DIR_PREFIX);
    properties.put(MultiTestRunner.SHOULD_PREPARE_ARGUMENT_NAME, "true");
    properties.put(MultiTestRunner.SHOULD_CLEAN_ARGUMENT_NAME, "true");
    properties.put(MultiTestRunner.REPORT_PATH_ARGUMENT_NAME, calculateReportPath().toString());
    return properties;
  }

  /**
   * Reads command-line options and processes them. For help option it displays usage information.
   * For configuration options it transforms them into key-value pairs.
   *
   * @param args command line arguments
   * @return Parse configuration parameters or null if help option has been provided
   */
  private static Optional<Map<String, String>> parseAndProcessArguments(String[] args)
      throws ConfigurationException, ParseException {
    Map<String, String> properties = new HashMap<>();
    CommandLineParser parser = new BasicParser();
    Options options = createCLIOptions();
    CommandLine cmd;
    try {
      cmd = parser.parse(options, args);
    } catch (ParseException e) {
      LOGGER.error("parseAndProcessArguments(): ParseException caught parsing arguments.", e);
      throw e;
    }

    if (cmd.hasOption(HELP_OPTION)) {
      HelpFormatter formatter = new HelpFormatter();
      formatter.printHelp(KademliaConsistencyCheckMain.class.getSimpleName(), options);
      return Optional.empty();
    }

    if (cmd.hasOption(CONFIG_FILE_OPTION)) {
      String argValue = cmd.getOptionValue(CONFIG_FILE_OPTION);
      XMLConfiguration config = new XMLConfiguration();
      config.setDelimiterParsingDisabled(true);
      try {
        config.load(argValue);
      } catch (ConfigurationException e) {
        LOGGER.error("parseAndProcessArguments(): ConfigurationException caught when reading"
            + " configuration file.", e);
        throw e;
      }
      properties.putAll(constructPropertiesFromRootsChildren(config));
    }

    if (cmd.hasOption(CONFIG_OPTION)) {
      String[] argValues = cmd.getOptionValues(CONFIG_OPTION);
      for (String arg : argValues) {
        String[] keyAndValue = arg.split("=", 2);
        String key = keyAndValue[0];
        String value = "";
        if (keyAndValue.length == 2) {
          value = keyAndValue[1];
        }
        properties.put(key, value);
      }
    }

    if (cmd.hasOption(ENV_FACTORY_OPTION)) {
      String argValue = cmd.getOptionValue(ENV_FACTORY_OPTION);
      switch (argValue) {
        case "local":
          mEnvironmentFactoryClass = LocalEnvironmentFactory.class;
          break;
        case "ssh":
          mEnvironmentFactoryClass = SSHEnvironmentFactory.class;
          break;
        default:
          String errorMsg = "Unknown environment factory " + argValue;
          LOGGER.error("parseAndProcessArguments(): {}", errorMsg);
          throw new IllegalArgumentException(errorMsg);
      }
    } else {
      String errorMsg = String.format("--%s option is required. Use --help to get usage help.",
          ENV_FACTORY_OPTION);
      LOGGER.error("parseAndProcessArguments(): {}", errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }

    return Optional.of(properties);
  }

  private KademliaConsistencyCheckMain() {
  }
}
