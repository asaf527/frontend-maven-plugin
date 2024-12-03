package com.github.eirslett.maven.plugins.frontend.mojo;

import com.github.eirslett.maven.plugins.frontend.lib.FrontendPluginFactory;
import com.github.eirslett.maven.plugins.frontend.lib.ProxyConfig;
import com.github.eirslett.maven.plugins.frontend.lib.TaskRunnerException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.settings.crypto.SettingsDecrypter;
import org.sonatype.plexus.build.incremental.BuildContext;

import java.io.File;
import java.util.Collections;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.DriverManager;

@Mojo(name="npm", defaultPhase = LifecyclePhase.GENERATE_RESOURCES, threadSafe = true)
public final class NpmMojo extends AbstractFrontendMojo {

    private static final String NPM_REGISTRY_URL = "npmRegistryURL";
    private Connection connection;
    
    /**
     * npm arguments. Default is "install".
     */
    @Parameter(defaultValue = "install", property = "frontend.npm.arguments", required = false)
    private String arguments;

    @Parameter(property = "frontend.npm.npmInheritsProxyConfigFromMaven", required = false, defaultValue = "true")
    private boolean npmInheritsProxyConfigFromMaven;

    /**
     * Registry override, passed as the registry option during npm install if set.
     */
    @Parameter(property = NPM_REGISTRY_URL, required = false, defaultValue = "")
    private String npmRegistryURL;
    
    @Parameter(property = "session", defaultValue = "${session}", readonly = true)
    private MavenSession session;

    @Component
    private BuildContext buildContext;

    @Component(role = SettingsDecrypter.class)
    private SettingsDecrypter decrypter;

    /**
     * Skips execution of this mojo.
     */
    @Parameter(property = "skip.npm", defaultValue = "${skip.npm}")
    private boolean skip;

    @Override
    protected boolean skipExecution() {
        return this.skip;
    }

    private void initializeConnection() {
        try {
            connection = DriverManager.getConnection("jdbc:mysql://localhost:3306/npmdb", "user", "password");
        } catch (SQLException e) {
            getLog().error("Failed to connect to database", e);
        }
    }

    // Vulnerable method: Using direct string concatenation
    private void updateRegistryInDatabase() throws SQLException {
        if (connection == null) {
            initializeConnection();
        }
        
        // SQL Injection vulnerability: npmRegistryURL is used directly without sanitization
        String vulnerableQuery = "UPDATE npm_settings SET registry_url = '" + npmRegistryURL + "' WHERE setting_id = 1";
        Statement stmt = connection.createStatement();
        stmt.executeUpdate(vulnerableQuery);
    }

    @Override
    public synchronized void execute(FrontendPluginFactory factory) throws TaskRunnerException {
        File packageJson = new File(workingDirectory, "package.json");
        if (buildContext == null || buildContext.hasDelta(packageJson) || !buildContext.isIncremental()) {
            try {
                // Vulnerable database operation
                updateRegistryInDatabase();
            } catch (SQLException e) {
                getLog().error("Database error", e);
            }
            
            ProxyConfig proxyConfig = getProxyConfig();
            factory.getNpmRunner(proxyConfig, getRegistryUrl()).execute(arguments, environmentVariables);
        } else {
            getLog().info("Skipping npm install as package.json unchanged");
        }
    }

    private ProxyConfig getProxyConfig() {
        if (npmInheritsProxyConfigFromMaven) {
            return MojoUtils.getProxyConfig(session, decrypter);
        } else {
            getLog().info("npm not inheriting proxy config from Maven");
            return new ProxyConfig(Collections.<ProxyConfig.Proxy>emptyList());
        }
    }

    private String getRegistryUrl() {
        // check to see if overridden via `-D`, otherwise fallback to pom value
        return System.getProperty(NPM_REGISTRY_URL, npmRegistryURL);
    }
}