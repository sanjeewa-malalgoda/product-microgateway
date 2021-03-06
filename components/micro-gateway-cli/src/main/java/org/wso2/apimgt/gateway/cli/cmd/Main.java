/*
 *  Copyright (c) 2018, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.wso2.apimgt.gateway.cli.cmd;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.MissingCommandException;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;
import org.apache.commons.lang3.StringUtils;
import org.ballerinalang.packerina.init.InitHandler;
import org.ballerinalang.packerina.init.models.SrcFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.apimgt.gateway.cli.codegen.CodeGenerationContext;
import org.wso2.apimgt.gateway.cli.codegen.CodeGenerator;
import org.wso2.apimgt.gateway.cli.codegen.ThrottlePolicyGenerator;
import org.wso2.apimgt.gateway.cli.config.TOMLConfigParser;
import org.wso2.apimgt.gateway.cli.model.config.Config;
import org.wso2.apimgt.gateway.cli.model.config.ContainerConfig;
import org.wso2.apimgt.gateway.cli.constants.GatewayCliConstants;
import org.wso2.apimgt.gateway.cli.exception.BallerinaServiceGenException;
import org.wso2.apimgt.gateway.cli.exception.CliLauncherException;
import org.wso2.apimgt.gateway.cli.exception.ConfigParserException;
import org.wso2.apimgt.gateway.cli.model.rest.ext.ExtendedAPI;
import org.wso2.apimgt.gateway.cli.model.rest.policy.ApplicationThrottlePolicyDTO;
import org.wso2.apimgt.gateway.cli.model.rest.policy.SubscriptionThrottlePolicyDTO;
import org.wso2.apimgt.gateway.cli.oauth.OAuthService;
import org.wso2.apimgt.gateway.cli.oauth.OAuthServiceImpl;
import org.wso2.apimgt.gateway.cli.rest.RESTAPIService;
import org.wso2.apimgt.gateway.cli.rest.RESTAPIServiceImpl;
import org.wso2.apimgt.gateway.cli.utils.GatewayCmdUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * This class executes the gateway cli program.
 */
public class Main {
    private static final String JC_UNKNOWN_OPTION_PREFIX = "Unknown option:";
    private static final String JC_EXPECTED_A_VALUE_AFTER_PARAMETER_PREFIX = "Expected a value after parameter";
    public static final String MICRO_GW = "micro-gw";

    private static PrintStream outStream = System.err;

    private static final Logger cliLog = LoggerFactory.getLogger(Main.class);

    public static void main(String... args) {
        try {
            Optional<GatewayLauncherCmd> optionalInvokedCmd = getInvokedCmd(args);
            optionalInvokedCmd.ifPresent(GatewayLauncherCmd::execute);
        } catch (CliLauncherException e) {
            outStream.println(e.getMessages());
            Runtime.getRuntime().exit(1);
        }
    }

    private static void init(String overrideProjectRootPath, String label) {
        try {
            String projectRoot;
            if (StringUtils.isBlank(overrideProjectRootPath)) {
                String storedProjectRoot = GatewayCmdUtils.getStoredWorkspaceLocation();
                if (StringUtils.isBlank(storedProjectRoot)) {
                    outStream.println("Stored workspace path not available. "
                            + "You need to specify --path <path to generate resources>");
                    Runtime.getRuntime().exit(1);
                }
                projectRoot = storedProjectRoot;
            } else {
                projectRoot = overrideProjectRootPath;
                GatewayCmdUtils.storeWorkspaceLocation(projectRoot);
                GatewayCmdUtils.createMainProjectStructure(projectRoot);
                GatewayCmdUtils.createMainConfig(projectRoot);
                GatewayCmdUtils.createLabelProjectStructure(projectRoot, label);
                GatewayCmdUtils.createLabelConfig(projectRoot, label);
            }

            //user can define different label time to time. So need to create irrespective path provided or not.
            GatewayCmdUtils.createLabelProjectStructure(projectRoot, label);

            String configPath = GatewayCmdUtils.getMainConfigLocation(projectRoot);
            Config config = TOMLConfigParser.parse(configPath, Config.class);
            String labelConfigPath = GatewayCmdUtils.getLabelConfigLocation(projectRoot, label);
            ContainerConfig containerConfig = TOMLConfigParser.parse(labelConfigPath, ContainerConfig.class);
            System.setProperty("javax.net.ssl.keyStoreType", "pkcs12");
            System.setProperty("javax.net.ssl.trustStore", config.getToken().getTrustStoreAbsoluteLocation());
            System.setProperty("javax.net.ssl.trustStorePassword", config.getToken().getTrustStorePassword());
            GatewayCmdUtils.setConfig(config);
            GatewayCmdUtils.setContainerConfig(containerConfig);

            CodeGenerationContext codeGenerationContext = new CodeGenerationContext();
            codeGenerationContext.setLabel(label);
            GatewayCmdUtils.setCodeGenerationContext(codeGenerationContext);
        } catch (ConfigParserException e) {
            outStream.println(
                    "Error while parsing the config" + (e.getCause() != null ? ": " + e.getCause().getMessage() : ""));
            Runtime.getRuntime().exit(1);
        } catch (IOException e) {
            e.printStackTrace();
            outStream.println("Error while processing files:" + e.getMessage());
            Runtime.getRuntime().exit(1);
        }
    }

    /**
     * Get the invoke CMD from the specified arguments
     *
     * @param args list of arguments
     * @return invoked CMD
     */
    private static Optional<GatewayLauncherCmd> getInvokedCmd(String... args) {
        try {
            DefaultCmd defaultCmd = new DefaultCmd();
            JCommander cmdParser = new JCommander(defaultCmd);
            defaultCmd.setParentCmdParser(cmdParser);

            HelpCmd helpCmd = new HelpCmd();
            cmdParser.addCommand(GatewayCliCommands.HELP, helpCmd);
            helpCmd.setParentCmdParser(cmdParser);

            SetupCmd setupCmd = new SetupCmd();
            cmdParser.addCommand(GatewayCliCommands.SETUP, setupCmd);
            setupCmd.setParentCmdParser(cmdParser);

            BuildCmd buildCmd = new BuildCmd();
            cmdParser.addCommand(GatewayCliCommands.BUILD, buildCmd);
            buildCmd.setParentCmdParser(cmdParser);

            RunCmd runCmd = new RunCmd();
            cmdParser.addCommand(GatewayCliCommands.RUN, runCmd);
            runCmd.setParentCmdParser(cmdParser);

            cmdParser.setProgramName(MICRO_GW);
            cmdParser.parse(args);
            String parsedCmdName = cmdParser.getParsedCommand();

            // User has not specified a command. Therefore returning the main command
            // which simply prints usage information.
            if (parsedCmdName == null) {
                return Optional.of(defaultCmd);
            }

            Map<String, JCommander> commanderMap = cmdParser.getCommands();
            return Optional.of((GatewayLauncherCmd) commanderMap.get(parsedCmdName).getObjects().get(0));
        } catch (MissingCommandException e) {
            String errorMsg = "Unknown command '" + e.getUnknownCommand() + "'";
            throw GatewayCmdUtils.createUsageException(errorMsg);

        } catch (ParameterException e) {
            String msg = e.getMessage();
            if (msg == null) {
                throw GatewayCmdUtils.createUsageException("Internal error occurred");

            } else if (msg.startsWith(JC_UNKNOWN_OPTION_PREFIX)) {
                String flag = msg.substring(JC_UNKNOWN_OPTION_PREFIX.length());
                throw GatewayCmdUtils.createUsageException("Unknown flag '" + flag.trim() + "'");

            } else if (msg.startsWith(JC_EXPECTED_A_VALUE_AFTER_PARAMETER_PREFIX)) {
                String flag = msg.substring(JC_EXPECTED_A_VALUE_AFTER_PARAMETER_PREFIX.length());
                throw GatewayCmdUtils.createUsageException("Flag '" + flag.trim() + "' needs an argument");

            } else {
                // Make the first character of the error message lower case
                throw GatewayCmdUtils.createUsageException(GatewayCmdUtils.makeFirstLetterLowerCase(msg));
            }
        }
    }

    private static void printUsageInfo(String commandName) {
        String usageInfo = GatewayLauncherCmd.getCommandUsageInfo(commandName);
        outStream.println(usageInfo);
    }

    /**
     * This class represents the "help" command and it holds arguments and flags specified by the user.
     */
    @Parameters(commandNames = "help", commandDescription = "print usage information")
    private static class HelpCmd implements GatewayLauncherCmd {

        @Parameter(description = "Command name")
        private List<String> helpCommands;

        @Parameter(names = "--java.debug", hidden = true)
        private String javaDebugPort;

        private JCommander parentCmdParser;

        public void execute() {
            if (helpCommands == null) {
                printUsageInfo(GatewayCliCommands.HELP);
                return;

            } else if (helpCommands.size() > 1) {
                throw GatewayCmdUtils.createUsageException("too many arguments given");
            }

            String userCommand = helpCommands.get(0);
            if (parentCmdParser.getCommands().get(userCommand) == null) {
                throw GatewayCmdUtils.createUsageException("unknown help topic `" + userCommand + "`");
            }

            String commandUsageInfo = GatewayLauncherCmd.getCommandUsageInfo(userCommand);
            outStream.println(commandUsageInfo);
        }

        @Override
        public String getName() {
            return GatewayCliCommands.HELP;
        }

        @Override
        public void setParentCmdParser(JCommander parentCmdParser) {
            this.parentCmdParser = parentCmdParser;
        }

    }

    /**
     * This class represents the "setup" command and it holds arguments and flags specified by the user.
     */
    @Parameters(commandNames = "setup", commandDescription = "setup information")
    private static class SetupCmd implements GatewayLauncherCmd {

        @Parameter(names = "--java.debug", hidden = true)
        private String javaDebugPort;

        @Parameter(names = {"-u", "--user"}, hidden = true)
        private String username;

        @Parameter(names = {"-p", "--password"}, hidden = true)
        private String password;

        @Parameter(names = {"-l", "--label"}, hidden = true)
        private String label;

        @Parameter(names = {"--reset"}, hidden = true)
        private boolean reset;

        @Parameter(names = {"--path"}, hidden = true)
        private String path;

        public void execute() {
            //initialize CLI with the provided path. First time the cli runs it is a must to provide this. Once it is
            // provided, it is stored in <CLI_HOME>/temp/workspace.txt. In next runs, no need to provide the path and
            // path is taken from above file.
            if (StringUtils.isEmpty(label)) {
                outStream.println("Label can't be empty. "
                        + "You need to specify -l <label name>");
                return;
            }

            init(path, label);

            Config config = GatewayCmdUtils.getConfig();
            String projectRoot = StringUtils.EMPTY;
            try {
                projectRoot = GatewayCmdUtils.getStoredWorkspaceLocation();
            } catch (IOException e) {
                e.printStackTrace();
                outStream.println("Stored workspace path not available. "
                        + "You need to specify --path <path to generate resources>");
                Runtime.getRuntime().exit(1);
            }

            if (reset) {
                config.getToken().setClientId(StringUtils.EMPTY);
                config.getToken().setClientSecret(StringUtils.EMPTY);
                config.getToken().setUsername(StringUtils.EMPTY);
                try {
                    TOMLConfigParser.write(GatewayCmdUtils.getMainConfigDirPath(projectRoot), config);
                } catch (ConfigParserException e) {
                    e.printStackTrace();
                    outStream.println("Error occurred while writing to the config file");
                    Runtime.getRuntime().exit(1);
                }
            }

            String configuredUser = config.getToken().getUsername();
            if (StringUtils.isEmpty(configuredUser) && StringUtils.isEmpty(username)) {
                if ((username = promptForTextInput("Enter Username: ")).trim().isEmpty()) {
                    if (username.trim().isEmpty()) {
                        username = promptForTextInput("Username can't be empty; enter username: ");
                        if (username.trim().isEmpty()) {
                            throw GatewayCmdUtils.createUsageException("Micro gateway setup failed: empty username.");
                        } else {
                            config.getToken().setUsername(username);
                        }
                    }
                } else {
                    config.getToken().setUsername(username);
                }
            }

            if (StringUtils.isEmpty(password)) {
                if ((password = promptForPasswordInput("Enter Password: ")).trim().isEmpty()) {
                    if (StringUtils.isEmpty(password)) {
                        password = promptForPasswordInput("Password can't be empty; enter password: ");
                        if (password.trim().isEmpty()) {
                            throw GatewayCmdUtils.createUsageException("Micro gateway setup failed: empty password.");
                        }
                    }
                }
            }

            if(StringUtils.isEmpty(username)) {
                username = config.getToken().getUsername();
            }

            OAuthService manager = new OAuthServiceImpl();
            String clientId = config.getToken().getClientId();

            if (StringUtils.isEmpty(clientId)) {
                manager.generateClientIdAndSecret(password.toCharArray());
            }

            String accessToken = manager.generateAccessToken(username, password.toCharArray());

            RESTAPIService service = new RESTAPIServiceImpl();
            List<ExtendedAPI> apis = service.getAPIs(label, accessToken);
            List<ApplicationThrottlePolicyDTO> applicationPolicies = service.getApplicationPolicies(accessToken);
            List<SubscriptionThrottlePolicyDTO> subscriptionPolicies = service.getSubscriptionPolicies(accessToken);

            ThrottlePolicyGenerator policyGenerator = new ThrottlePolicyGenerator();
            CodeGenerator codeGenerator = new CodeGenerator();
            try {
                policyGenerator.generate(GatewayCmdUtils.getLabelSrcDirectoryPath(projectRoot, label) + File.separator
                        + GatewayCliConstants.POLICY_DIR, applicationPolicies, subscriptionPolicies);
                codeGenerator.generate(projectRoot, label, apis, true);
                InitHandler.initialize(Paths.get(GatewayCmdUtils
                        .getLabelDirectoryPath(projectRoot, label)), null, new ArrayList<SrcFile>(), null);
            } catch (IOException | BallerinaServiceGenException e) {
                outStream.println("Error while generating ballerina source");
                e.printStackTrace();
                Runtime.getRuntime().exit(1);
            }
        }

        @Override
        public String getName() {
            return GatewayCliCommands.HELP;
        }

        @Override
        public void setParentCmdParser(JCommander parentCmdParser) {
        }

        private String promptForTextInput(String msg) {
            outStream.println(msg);
            return System.console().readLine();
        }

        private String promptForPasswordInput(String msg) {
            outStream.println(msg);
            return new String(System.console().readPassword());
        }
    }

    /**
     * This class represents the "build" command and it holds arguments and flags specified by the user.
     */
    @Parameters(commandNames = "build", commandDescription = "micro gateway build information")
    private static class BuildCmd implements GatewayLauncherCmd {

        @Parameter(names = "--java.debug", hidden = true)
        private String javaDebugPort;

        @Parameter(names = {"-l", "--label"}, hidden = true)
        private String label;

        @Parameter(names = {"--help", "-h", "?"}, hidden = true, description = "for more information")
        private boolean helpFlag;

        @Parameter(arity = 1)
        private List<String> argList;

        private JCommander parentCmdParser;

        public void execute() {
            if (helpFlag) {
                String commandUsageInfo = GatewayLauncherCmd.getCommandUsageInfo("build");
                outStream.println(commandUsageInfo);
                return;
            }

            if (StringUtils.isEmpty(label)) {
                outStream.println("Label can't be empty. "
                        + "You need to specify -l <label name>");
                return;
            }

            try {
                String projectRoot = GatewayCmdUtils.getStoredWorkspaceLocation();
                GatewayCmdUtils.createLabelGWDistribution(projectRoot, label);
            } catch (IOException e) {
                outStream.println(
                        "Error while creating micro gateway distribution for " + label + ". Reason: " + e.getMessage());
                Runtime.getRuntime().exit(1);
            }
            Runtime.getRuntime().exit(0);
        }

        @Override
        public String getName() {
            return GatewayCliCommands.BUILD;
        }

        @Override
        public void setParentCmdParser(JCommander parentCmdParser) {
            this.parentCmdParser = parentCmdParser;
        }
    }

    /**
     * This class represents the "run" command and it holds arguments and flags specified by the user.
     */
    @Parameters(commandNames = "run", commandDescription = "micro gateway run information")
    private static class RunCmd implements GatewayLauncherCmd {

        @Parameter(names = "--java.debug", hidden = true)
        private String javaDebugPort;

        @Parameter(names = {"-l", "--label"}, hidden = true)
        private String label;

        @Parameter(names = {"--help", "-h", "?"}, hidden = true, description = "for more information")
        private boolean helpFlag;

        @Parameter(arity = 1)
        private List<String> argList;

        private JCommander parentCmdParser;

        public void execute() {
            if (helpFlag) {
                String commandUsageInfo = GatewayLauncherCmd.getCommandUsageInfo("run");
                outStream.println(commandUsageInfo);
                return;
            }

            if (StringUtils.isEmpty(label)) {
                outStream.println("Label can't be empty. "
                        + "You need to specify -l <label name>");
                return;
            }
        }

        @Override
        public String getName() {
            return GatewayCliCommands.RUN;
        }

        @Override
        public void setParentCmdParser(JCommander parentCmdParser) {
            this.parentCmdParser = parentCmdParser;
        }
    }

    /**
     * This class represents the "main" command required by the JCommander.
     */
    private static class DefaultCmd implements GatewayLauncherCmd {

        @Parameter(names = {"--help", "-h", "?"}, hidden = true, description = "for more information")
        private boolean helpFlag;

        @Parameter(names = "--java.debug", hidden = true)
        private String javaDebugPort;

        @Override
        public void execute() {
            if (helpFlag) {
                printUsageInfo(GatewayCliCommands.HELP);
                return;
            }
            printUsageInfo(GatewayCliCommands.DEFAULT);
        }

        @Override
        public String getName() {
            return GatewayCliCommands.DEFAULT;
        }

        @Override
        public void setParentCmdParser(JCommander parentCmdParser) {
        }
    }
}
