
package com.akiban.server.service.log4jconfig;

public class Log4JConfigurationMXBeanSingleton implements Log4JConfigurationMXBean {
    private final static Log4JConfigurationMXBean instance = new Log4JConfigurationMXBeanSingleton();

    public static Log4JConfigurationMXBean instance() {
        return instance;
    }

    Log4JConfigurationMXBeanSingleton() {
        // nothing
    }

    private final Object MONITOR = new Object();
    private Long updateFrequency = null;
    private String configFile = null;

    @Override
    public final String getConfigurationFile() {
        synchronized (MONITOR) {
            return configFile;
        }
    }

    @Override
    public final void setConfigurationFile(String configFile) {
        if (configFile == null) {
            throw new IllegalArgumentException("config file may not be null");
        }
        synchronized (MONITOR) {
            if(this.updateFrequency != null) {
                throw new IllegalStateException("Can't set config file after polling has started");
            }
            this.configFile = configFile;
        }
        configure(configFile);
    }

    @Override
    public final Long getUpdateFrequencyMS() {
        synchronized (MONITOR) {
            return updateFrequency;
        }
    }

    @Override
    public final void pollConfigurationFile(String file, long updateFrequencyMS) {
        if (file == null) {
            throw new IllegalArgumentException("file may not be null");
        }
        if (updateFrequencyMS <= 0) {
            throw new IllegalArgumentException("updateFrequencyMS must be positive (tried to pass in "
                    + updateFrequencyMS + ')');
        }

        synchronized (MONITOR) {
            if (this.updateFrequency != null) {
                throw new IllegalStateException("Can't set config file or polling frequency after polling has started");
            }
            this.configFile = file;
            this.updateFrequency = updateFrequencyMS;
        }

        configureAndWatch(file, updateFrequencyMS);
    }

    @Override
    public final void pollConfigurationFile(long updateFrequencyMS) {
        final String configFileLocal = getConfigurationFile();
        if (configFileLocal == null) {
            throw new IllegalStateException("can't start polling until you set a config file");
        }
        pollConfigurationFile(configFileLocal, updateFrequencyMS);
    }

    @Override
    public final void updateConfigurationFile() {
        String configFileLocal = getConfigurationFile();
        if (configFileLocal == null) {
            throw new IllegalStateException("can't update file until it's set");
        }
        configure(configFileLocal);
    }

    protected void configure(String configFile) {
        org.apache.log4j.PropertyConfigurator.configure(configFile);
    }

    protected void configureAndWatch(String configFile, long updateFrequency) {
        org.apache.log4j.PropertyConfigurator.configureAndWatch(configFile, updateFrequency);
    }
}
