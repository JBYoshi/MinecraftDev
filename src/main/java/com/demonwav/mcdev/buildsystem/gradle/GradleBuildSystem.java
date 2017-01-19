/*
 * Minecraft Dev for IntelliJ
 *
 * https://minecraftdev.org
 *
 * Copyright (c) 2017 minecraft-dev
 *
 * MIT License
 */

package com.demonwav.mcdev.buildsystem.gradle;

import com.demonwav.mcdev.buildsystem.BuildDependency;
import com.demonwav.mcdev.buildsystem.BuildRepository;
import com.demonwav.mcdev.buildsystem.BuildSystem;
import com.demonwav.mcdev.creator.MinecraftProjectCreator;
import com.demonwav.mcdev.platform.AbstractTemplate;
import com.demonwav.mcdev.platform.PlatformType;
import com.demonwav.mcdev.platform.ProjectConfiguration;
import com.demonwav.mcdev.platform.forge.ForgeProjectConfiguration;
import com.demonwav.mcdev.platform.forge.ForgeTemplate;
import com.demonwav.mcdev.platform.hybrid.SpongeForgeProjectConfiguration;
import com.demonwav.mcdev.platform.liteloader.LiteLoaderProjectConfiguration;
import com.demonwav.mcdev.platform.liteloader.LiteLoaderTemplate;
import com.demonwav.mcdev.platform.sponge.SpongeTemplate;
import com.demonwav.mcdev.util.Util;

import com.intellij.codeInsight.actions.ReformatCodeProcessor;
import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.application.ApplicationConfiguration;
import com.intellij.execution.application.ApplicationConfigurationType;
import com.intellij.execution.impl.RunManagerImpl;
import com.intellij.execution.impl.RunnerAndConfigurationSettingsImpl;
import com.intellij.externalSystem.JavaProjectData;
import com.intellij.ide.actions.ImportModuleAction;
import com.intellij.ide.util.newProjectWizard.AddModuleWizard;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ExternalProjectInfo;
import com.intellij.openapi.externalSystem.model.project.ExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemJdkUtil;
import com.intellij.openapi.externalSystem.service.execution.ExternalSystemRunConfiguration;
import com.intellij.openapi.externalSystem.service.project.manage.ProjectDataManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiManager;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ProgressListener;
import org.gradle.tooling.ProjectConnection;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.AsyncPromise;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.service.execution.GradleExternalTaskConfigurationType;
import org.jetbrains.plugins.gradle.service.project.data.ExternalProjectDataCache;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportBuilder;
import org.jetbrains.plugins.gradle.service.project.wizard.GradleProjectImportProvider;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrCommandArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementFactoryImpl;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GradleBuildSystem extends BuildSystem {

    private final static Logger logger = Logger.getLogger(GradleBuildSystem.class);

    @Nullable
    private VirtualFile buildGradle;

    @Nullable
    public VirtualFile getBuildGradle() {
        return buildGradle;
    }

    @Override
    public void create(@NotNull Project project, @NotNull ProjectConfiguration configuration, @NotNull ProgressIndicator indicator) {
        getRootDirectory().refresh(false, true);
        createDirectories();

        if (configuration.type == PlatformType.FORGE || configuration instanceof SpongeForgeProjectConfiguration) {
            if (!(configuration instanceof ForgeProjectConfiguration)) {
                return;
            }

            ForgeProjectConfiguration settings = (ForgeProjectConfiguration) configuration;
            Util.runWriteTask(() -> {
                try {
                    final VirtualFile gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");

                    buildGradle = getRootDirectory().findOrCreateChildData(this, "build.gradle");

                    ForgeTemplate.applyBuildGradleTemplate(
                        project,
                        buildGradle,
                        gradleProp,
                        getGroupId(),
                        getArtifactId(),
                        settings.forgeVersion,
                        settings.mcpVersion,
                        getVersion(),
                        configuration instanceof SpongeForgeProjectConfiguration
                    );

                    if (configuration instanceof SpongeForgeProjectConfiguration) {
                        PsiFile buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle);
                        if (buildGradlePsi != null) {
                            addBuildGradleDependencies(project, buildGradlePsi, false);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            setupWrapper(project, indicator);
            setupDecompWorkspace(project, indicator);
        } else if (configuration.type == PlatformType.LITELOADER) {
            if (!(configuration instanceof LiteLoaderProjectConfiguration)) {
                return;
            }

            LiteLoaderProjectConfiguration settings = (LiteLoaderProjectConfiguration) configuration;
            Util.runWriteTask(() -> {
                try {
                    final VirtualFile gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");

                    buildGradle = getRootDirectory().findOrCreateChildData(this, "build.gradle");

                    LiteLoaderTemplate.applyBuildGradleTemplate(
                        project,
                        buildGradle,
                        gradleProp,
                        getGroupId(),
                        getArtifactId(),
                        settings.pluginVersion,
                        settings.mcVersion,
                        settings.mcpVersion
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            setupWrapper(project, indicator);
            setupDecompWorkspace(project, indicator);
        } else {
            Util.runWriteTask(() -> {
                try {
                    final VirtualFile gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");

                    String buildGradleText;
                    if (configuration.type == PlatformType.SPONGE) {
                        buildGradleText = SpongeTemplate.applyBuildGradleTemplate(
                            project,
                            gradleProp,
                            getGroupId(),
                            getArtifactId(),
                            getVersion(),
                            getBuildVersion()
                        );
                    } else {
                        buildGradleText = AbstractTemplate.applyBuildGradleTemplate(project, gradleProp,
                                                                                    getGroupId(),
                                                                                    getVersion(),
                                                                                    getBuildVersion()
                        );
                    }

                    if (buildGradleText == null) {
                        return;
                    }

                    addBuildGradleDependencies(project, buildGradleText);

                    VirtualFileManager.getInstance().refreshWithoutFileWatcher(false);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            setupWrapper(project, indicator);
        }

        // The file needs to be saved, if not Gradle will see the file without the dependencies and won't import correctly
        if (buildGradle == null) {
            return;
        }

        saveFile(buildGradle);
    }

    private void setupWrapper(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        // Setup gradle wrapper
        // We'll write the properties file to ensure it sets up with the right version
        Util.runWriteTask(() -> {
            try {
                String wrapperDirPath = getRootDirectory().createChildDirectory(this, "gradle").createChildDirectory(this, "wrapper").getPath();
                FileUtils.writeLines(new File(wrapperDirPath, "gradle-wrapper.properties"), Collections.singletonList(
                    "distributionUrl=https\\://services.gradle.org/distributions/gradle-2.14.1-bin.zip"
                ));
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        // Use gradle tooling to run the wrapper task
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File(getRootDirectory().getPath()));
        ProjectConnection connection = connector.connect();
        BuildLauncher launcher = connection.newBuild();
        try {
            Pair<String, Sdk> sdkPair = ExternalSystemJdkUtil.getAvailableJdk(project);
            if (sdkPair != null && sdkPair.getSecond() != null && sdkPair.getSecond().getHomePath() != null &&
                !ExternalSystemJdkUtil.USE_INTERNAL_JAVA.equals(sdkPair.getFirst())) {

                launcher.setJavaHome(new File(sdkPair.getSecond().getHomePath()));
            }

            launcher.forTasks("wrapper").addProgressListener((ProgressListener) progressEvent ->
                indicator.setText(progressEvent.getDescription())
            ).run();
        } finally {
            connection.close();
        }
    }

    private void createRepositoriesOrDependencies(@NotNull Project project,
                                                  @NotNull GroovyFile file,
                                                  @NotNull String name,
                                                  @NotNull List<String> expressions) {

        // Get the block so we can start working with it
        GrClosableBlock block = getClosableBlockByName(file, name);

        if (block == null) {
            return;
        }

        // Create a super expression with all the expressions tied together
        String expressionText = expressions.stream().collect(Collectors.joining("\n"));

        // We can't create each expression and add them to the file...that won't work. Groovy requires a new line
        // from one method call expression to another, and there's no way to put whitespace in Psi because Psi is
        // stupid. So instead we make the whole thing as one big clump and insert it into the block
        GroovyFile fakeFile = GroovyPsiElementFactoryImpl.getInstance(project).createGroovyFile(expressionText, false, null);
        PsiElement last = block.getChildren()[block.getChildren().length - 1];
        block.addBefore(fakeFile, last);
    }

    @Nullable
    private GrClosableBlock getClosableBlockByName(@NotNull PsiElement element, @NotNull String name) {
        List<GrClosableBlock> blocks = getClosableBlocksByName(element, name);
        if (blocks.isEmpty()) {
            return null;
        } else {
            return blocks.get(0);
        }
    }

    @NotNull
    private List<GrClosableBlock> getClosableBlocksByName(@NotNull PsiElement element, @NotNull String name) {
        return Arrays.stream(element.getChildren())
            .filter(c -> {
                // We want to find the child which has a GrReferenceExpression with the right name
                return Arrays.stream(c.getChildren())
                             .anyMatch(g -> g instanceof GrReferenceExpression && g.getText().equals(name));
            }).map(c -> {
                // We want to find the grandchild which is a GrCloseableBlock, this is the
                // basis for the method block
                return Arrays.stream(c.getChildren())
                    .filter(g -> g instanceof GrClosableBlock)
                    // cast to closable block so generics can handle this conversion
                    .map(g -> (GrClosableBlock) g)
                    .findFirst();
            }).filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    @Override
    public void finishSetup(@NotNull Module rootModule, @NotNull Collection<? extends ProjectConfiguration> configurations, @NotNull ProgressIndicator indicator) {
        Project project = rootModule.getProject();

        // Tell Gradle to import this project
        final ProjectDataManager projectDataManager = ServiceManager.getService(ProjectDataManager.class);
        GradleProjectImportBuilder gradleProjectImportBuilder = new GradleProjectImportBuilder(projectDataManager);
        final GradleProjectImportProvider gradleProjectImportProvider = new GradleProjectImportProvider(gradleProjectImportBuilder);
        if (buildGradle != null) {
            indicator.setText("Running Gradle Setup");
            ApplicationManager.getApplication().invokeLater(() -> {
                AddModuleWizard wizard = new AddModuleWizard(project, buildGradle.getPath(), gradleProjectImportProvider);
                if (wizard.showAndGet()) {
                    ImportModuleAction.createFromWizard(project, wizard);
                }

                // Set up the run config
                // Get the gradle external task type, this is what set's it as a gradle task
                GradleExternalTaskConfigurationType gradleType = GradleExternalTaskConfigurationType.getInstance();

                // Set the Forge client and server run configs
                if (configurations.stream().anyMatch(c -> c.type == PlatformType.FORGE || c instanceof SpongeForgeProjectConfiguration)) {
                    Module mainModule;
                    if (configurations.size() == 1) {
                        mainModule = ModuleManager.getInstance(project).findModuleByName(rootModule.getName() + "_main");
                    } else {
                        mainModule = ModuleManager.getInstance(project).findModuleByName(rootModule.getName() + "-forge_main");
                    }
                    Module forgeModule = ModuleManager.getInstance(project).findModuleByName(rootModule.getName() + "-forge");

                    // Client run config
                    ApplicationConfiguration runClientConfiguration = new ApplicationConfiguration(
                        (forgeModule != null ? forgeModule : rootModule).getName() + " run client",
                        project,
                        ApplicationConfigurationType.getInstance()
                    );
                    File runningDir = new File(project.getBasePath(), "run");
                    if (!runningDir.exists()) {
                        //noinspection ResultOfMethodCallIgnored
                        runningDir.mkdir();
                    }
                    runClientConfiguration.setWorkingDirectory(project.getBasePath() + File.separator + "run");
                    runClientConfiguration.setMainClassName("GradleStart");
                    if (configurations.size() == 1) {
                        runClientConfiguration.setModule(mainModule != null ? mainModule : rootModule);
                    } else {
                        runClientConfiguration.setModule(mainModule != null ? mainModule : forgeModule != null ? forgeModule : rootModule);
                    }
                    RunnerAndConfigurationSettings clientSettings = new RunnerAndConfigurationSettingsImpl(
                        RunManagerImpl.getInstanceImpl(project),
                        runClientConfiguration,
                        false
                    );
                    clientSettings.setActivateToolWindowBeforeRun(true);
                    clientSettings.setSingleton(true);
                    RunManager.getInstance(project).addConfiguration(clientSettings, false);
                    RunManager.getInstance(project).setSelectedConfiguration(clientSettings);

                    // Server run config
                    ApplicationConfiguration runServerConfiguration = new ApplicationConfiguration(
                        rootModule.getName() + " run server",
                        project,
                        ApplicationConfigurationType.getInstance()
                    );
                    runServerConfiguration.setMainClassName("GradleStartServer");
                    runServerConfiguration.setProgramParameters("nogui");
                    runServerConfiguration.setWorkingDirectory(project.getBasePath() + File.separator + "run");
                    if (configurations.size() == 1) {
                        runServerConfiguration.setModule(mainModule != null ? mainModule : rootModule);
                    } else {
                        runServerConfiguration.setModule(mainModule != null ? mainModule : forgeModule != null ? forgeModule : rootModule);
                    }
                    RunnerAndConfigurationSettings serverSettings = new RunnerAndConfigurationSettingsImpl(
                        RunManagerImpl.getInstanceImpl(project),
                        runServerConfiguration,
                        false
                    );
                    serverSettings.setActivateToolWindowBeforeRun(true);
                    serverSettings.setSingleton(true);
                    RunManager.getInstance(project).addConfiguration(serverSettings, false);
                }

                // Create a gradle external system run config
                ExternalSystemRunConfiguration runConfiguration = new ExternalSystemRunConfiguration(
                    GradleConstants.SYSTEM_ID,
                    project,
                    gradleType.getConfigurationFactories()[0],
                    rootModule.getName() + " build"
                );
                // Set relevant gradle values
                runConfiguration.getSettings().setExternalProjectPath(getRootDirectory().getPath());
                runConfiguration.getSettings().setExecutionName(rootModule.getName() + " build");
                runConfiguration.getSettings().setTaskNames(Collections.singletonList("build"));
                // Create a RunAndConfigurationSettings object, which defines general settings for the run configuration
                RunnerAndConfigurationSettings settings = new RunnerAndConfigurationSettingsImpl(
                    RunManagerImpl.getInstanceImpl(project),
                    runConfiguration,
                    false
                );
                // Open the tool window and set it as a singleton run types
                settings.setActivateToolWindowBeforeRun(true);
                settings.setSingleton(true);

                // Apply the run config and select it
                RunManager.getInstance(project).addConfiguration(settings, false);
            }, ModalityState.NON_MODAL);
        }
    }


    @NotNull
    @Override
    public Promise<BuildSystem> reImport(@NotNull Module module) {
        if (synchronize()) {
            final AsyncPromise<BuildSystem> importPromise = getImportPromise();
            assert importPromise != null;
            return importPromise;
        }
        final AsyncPromise<BuildSystem> importPromise = getImportPromise();
        assert importPromise != null;

        // We must be on the event dispatch thread to run a backgroundable task
        ApplicationManager.getApplication().invokeLater(() ->
            ProgressManager.getInstance().run(new Task.Backgroundable(module.getProject(), "Importing Gradle Module", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    // We will need to request read access, which we can do from async
                    ApplicationManager.getApplication().runReadAction(() -> {
                        Project project = module.getProject();

                        // root directory is the first content root
                        ModuleRootManager manager = ModuleRootManager.getInstance(module);
                        if (manager.getContentRoots().length == 0) {
                            // TODO handle import failed
                            logger.error("GradleBuildSystem import FAILED: no content roots found");
                            importPromise.setResult(GradleBuildSystem.this);
                            return;
                        }

                        setRootDirectory(manager.getContentRoots()[0]);
                        buildGradle = getRootDirectory().findChild("build.gradle");

                        if (getRootDirectory().getCanonicalPath() == null || buildGradle == null) {
                            logger.error("GradleBuildSystem import FAILED: Root Directory or Build Gradle paths null");
                            logger.error("rootDirectory: " + getRootDirectory());
                            logger.error("buildGradle: " + buildGradle);
                            importPromise.setResult(GradleBuildSystem.this);
                            return;
                        }

                        setSourceDirectories(new ArrayList<>());
                        setResourceDirectories(new ArrayList<>());
                        setTestSourcesDirectories(new ArrayList<>());
                        setTestResourceDirectories(new ArrayList<>());

                        ExternalProjectDataCache externalProjectDataCache = ExternalProjectDataCache.getInstance(project);

                        String name = module.getName();
                        // I can't find a way to read module group children, group path only goes up
                        // So I guess check each module to see if it's a child....
                        Collection<Module> children = Arrays.stream(ModuleManager.getInstance(project).getModules())
                            .filter(m -> {
                                String[] paths = ModuleManager.getInstance(project).getModuleGroupPath(m);
                                if (paths != null && paths.length > 0) {
                                    if (name.equals(paths[paths.length - 1])) {
                                        return true;
                                    }
                                }
                                return false;
                            }).collect(Collectors.toList());

                        if (project.getBasePath() == null) {
                            logger.error("GradleBuildSystem import FAILED: Project base path null");
                            importPromise.setResult(GradleBuildSystem.this);
                            return;
                        }

                        // We need to check the parent too if it's a single module project
                        ExternalProject externalRootProject = externalProjectDataCache
                            .getRootExternalProject(GradleConstants.SYSTEM_ID, new File(getRootDirectory().getCanonicalPath()));
                        if (externalRootProject != null) {
                            for (Module child : children) {
                                Map<String, ExternalSourceSet> externalSourceSets = externalProjectDataCache
                                    .findExternalProject(externalRootProject, child);

                                for (ExternalSourceSet sourceSet : externalSourceSets.values()) {
                                    setupDirs(getSourceDirectories(), sourceSet, ExternalSystemSourceType.SOURCE);
                                    setupDirs(getResourceDirectories(), sourceSet, ExternalSystemSourceType.RESOURCE);
                                    setupDirs(getTestSourcesDirectories(), sourceSet, ExternalSystemSourceType.TEST);
                                    setupDirs(getTestResourceDirectories(), sourceSet, ExternalSystemSourceType.TEST_RESOURCE);
                                }
                            }

                            setGroupId(externalRootProject.getGroup());
                            setArtifactId(externalRootProject.getName());
                            setVersion(externalRootProject.getVersion());

                            setPluginName(externalRootProject.getName());

                            // We need to get the project info from gradle
                            ExternalProjectInfo info = ProjectDataManager.getInstance()
                                .getExternalProjectData(project, GradleConstants.SYSTEM_ID, getRootDirectory().getCanonicalPath());
                            if (info == null) {
                                logger.error("GradleBuildSystem import FAILED: External project info null");
                                importPromise.setResult(GradleBuildSystem.this);
                                return;
                            }

                            DataNode<ProjectData> node = info.getExternalProjectStructure();

                            if (node == null) {
                                logger.error("GradleBuildSystem import FAILED: Project data node null");
                                importPromise.setResult(GradleBuildSystem.this);
                                return;
                            }

                            setDependencies(new ArrayList<>());
                            node.getChildren().forEach(child -> {
                                if (child.getData() instanceof LibraryData) {
                                    LibraryData data = (LibraryData) child.getData();
                                    String[] parts = data.getExternalName().split(":");
                                    if (parts.length < 3) {
                                        logger.warn("LibraryData held incompatible data: " + data.getExternalName());
                                        return;
                                    }
                                    String groupId = parts[0];
                                    String artifactId = parts[1];
                                    String version = parts[2];
                                    // I should probably remove scope, as I'm not even using it here...
                                    getDependencies().add(new BuildDependency(groupId, artifactId, version, "provided"));
                                } else if (child.getData() instanceof JavaProjectData) {
                                    // kashike guilt tripped me into this
                                    JavaProjectData data = (JavaProjectData) child.getData();
                                    String languageLevelName = data.getLanguageLevel().name();
                                    int index = languageLevelName.lastIndexOf('_') - 1;
                                    if (index != -1) {
                                        setBuildVersion(languageLevelName.substring(index, languageLevelName.length()).replace("_", "."));
                                    }
                                }
                            });
                        }

                        GroovyFile groovyFile = (GroovyFile) PsiManager.getInstance(project).findFile(buildGradle);
                        if (groovyFile != null) {
                            // get repositories
                            setRepositories(new ArrayList<>());
                            // We need to climb the tree to get to the repositories

                            GrClosableBlock block = getClosableBlockByName(groovyFile, "repositories");
                            if (block != null) {
                                addRepositories(block);
                            }
                        }
                    });
                    importPromise.setResult(GradleBuildSystem.this);
                }
            })
        );
        return importPromise;
    }

    @NotNull
    public Map<GradleBuildSystem, ProjectConfiguration> createMultiModuleProject(@NotNull Project project,
                                                                                 @NotNull Map<PlatformType, ProjectConfiguration> configurations,
                                                                                 @NotNull ProgressIndicator indicator) {

        final Map<GradleBuildSystem, ProjectConfiguration> map = new HashMap<>();

        setupWrapper(project, indicator);

        getRootDirectory().refresh(false, true);

        // Create the includes string for settings.gradle
        // First, we add the common module that all multi-module projects will have
        String tempIncludes = "'" + getPluginName().toLowerCase() + "-common', ";
        // We use an iterator because we need to know when there won't be a next entry
        Iterator<ProjectConfiguration> configurationIterator = configurations.values().iterator();
        while (configurationIterator.hasNext()) {
            ProjectConfiguration configuration = configurationIterator.next();
            tempIncludes += "'" + getPluginName().toLowerCase() + "-" + configuration.type.name().toLowerCase() + "'";
            // Only add the ending comma after the entry when there is another entry to add
            if (configurationIterator.hasNext()) {
                tempIncludes += ", ";
            }
        }

        String includes = tempIncludes;
        Util.runWriteTask(() -> {
            try {
                // Write the parent files to disk so the children modules can import correctly
                buildGradle = getRootDirectory().createChildData(this, "build.gradle");
                final VirtualFile gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");
                final VirtualFile settingsGradle = getRootDirectory().createChildData(this, "settings.gradle");

                AbstractTemplate.applyMultiModuleBuildGradleTemplate(project, buildGradle, gradleProp,
                                                                     getGroupId(),
                                                                     getVersion(),
                                                                     getBuildVersion()
                );

                AbstractTemplate.applySettingsGradleTemplate(project, settingsGradle, getArtifactId().toLowerCase(), includes);

                // Common will be empty, it's for the developer to fill in with common classes
                VirtualFile common = getRootDirectory().createChildDirectory(this, getArtifactId().toLowerCase() + "-common");
                createDirectories(common);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });

        for (ProjectConfiguration configuration : configurations.values()) {
            // We associate each configuration with the given build system, which we add to the map at the end of this method
            GradleBuildSystem gradleBuildSystem = new GradleBuildSystem();
            Util.runWriteTask(() -> {
                try {
                    // Add settings for the new build system before it creates the module
                    gradleBuildSystem.setRootDirectory(getRootDirectory()
                        .createChildDirectory(this,
                            getArtifactId().toLowerCase() + "-" + configuration.type.name()
                                .toLowerCase()
                        ));

                    gradleBuildSystem.setArtifactId(getArtifactId());
                    gradleBuildSystem.setGroupId(getGroupId());
                    gradleBuildSystem.setVersion(getVersion());

                    gradleBuildSystem.setDependencies(new ArrayList<>());
                    gradleBuildSystem.setRepositories(new ArrayList<>());

                    gradleBuildSystem.setPluginName(getPluginName());
                    gradleBuildSystem.setBuildVersion(getBuildVersion());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            // it knows which dependencies are needed for each configuration
            MinecraftProjectCreator.addDependencies(configuration, gradleBuildSystem);

            // For each build system we initialize it, but not the same as a normal create. We need to know the common
            // project name, as we automatically add it as a dependency too
            gradleBuildSystem.createSubModule(project, configuration, getArtifactId().toLowerCase() + "-common", indicator);
            map.put(gradleBuildSystem, configuration);
        }

        return map;
    }

    private void createSubModule(@NotNull Project project,
                                 @NotNull ProjectConfiguration configuration,
                                 @NotNull String commonProjectName,
                                 @NotNull ProgressIndicator indicator) {
        getRootDirectory().refresh(false, true);
        createDirectories();

        // This is mostly the same as a normal create, but we use different files and don't setup the wrapper
        if (configuration.type == PlatformType.FORGE || configuration instanceof SpongeForgeProjectConfiguration) {
            if (!(configuration instanceof ForgeProjectConfiguration)) {
                return;
            }

            ForgeProjectConfiguration settings = (ForgeProjectConfiguration) configuration;
            Util.runWriteTask(() -> {
                final VirtualFile gradleProp;
                try {
                    gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    buildGradle = getRootDirectory().findOrCreateChildData(this, "build.gradle");

                    ForgeTemplate.applySubmoduleBuildGradleTemplate(
                        project,
                        buildGradle,
                        gradleProp,
                        getArtifactId(),
                        settings.forgeVersion,
                        settings.mcpVersion,
                        commonProjectName,
                        configuration instanceof SpongeForgeProjectConfiguration
                    );

                    // We're only going to write the dependencies if it's a sponge forge project
                    if (configuration instanceof SpongeForgeProjectConfiguration) {
                        PsiFile buildGradlePsi = PsiManager.getInstance(project).findFile(buildGradle);
                        if (buildGradlePsi != null) {
                            addBuildGradleDependencies(project, buildGradlePsi, false);
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            setupDecompWorkspace(project, indicator);
        } else if (configuration.type == PlatformType.LITELOADER) {
            if (!(configuration instanceof LiteLoaderProjectConfiguration)) {
                return;
            }

            LiteLoaderProjectConfiguration settings = (LiteLoaderProjectConfiguration) configuration;
            Util.runWriteTask(() -> {
                final VirtualFile gradleProp;
                try {
                    gradleProp = getRootDirectory().findOrCreateChildData(this, "gradle.properties");
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }

                try {
                    buildGradle = getRootDirectory().findOrCreateChildData(this, "build.gradle");

                    LiteLoaderTemplate.applySubmoduleBuildGradleTemplate(
                        project,
                        buildGradle,
                        gradleProp,
                        settings.pluginVersion,
                        settings.mcVersion,
                        settings.mcpVersion,
                        commonProjectName
                    );
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

            setupDecompWorkspace(project, indicator);
        } else {
            Util.runWriteTask(() -> {
                String buildGradleText;
                if (configuration.type == PlatformType.SPONGE) {
                    buildGradleText = SpongeTemplate.applySubmoduleBuildGradleTemplate(project, commonProjectName);
                } else {
                    buildGradleText = AbstractTemplate.applySubmoduleBuildGradleTemplate(project, commonProjectName);
                }

                if (buildGradleText == null) {
                    return;
                }

                addBuildGradleDependencies(project, buildGradleText);
            });
        }

        // The file needs to be saved, if not Gradle will see the file without the dependencies and won't import correctly
        if (buildGradle == null) {
            return;
        }

        saveFile(buildGradle);
    }

    private void addBuildGradleDependencies(@NotNull Project project, @NotNull PsiFile file, boolean addToDirectory) {
        // Write the repository and dependency data to the psi file
        new WriteCommandAction.Simple(project, file) {
            @Override
            protected void run() throws Throwable {
                final VirtualFile buildGradle = getRootDirectory().findOrCreateChildData(this, "build.gradle");

                file.setName("build.gradle");
                final GroovyFile groovyFile = (GroovyFile) file;

                // Add repositories
                createRepositoriesOrDependencies(
                    project,
                    groovyFile,
                    "repositories",
                    getRepositories().stream()
                                     .map(r -> String.format("maven {name = '%s'\nurl = '%s'\n}", r.getId(), r.getUrl()))
                                     .collect(Collectors.toList())
                );

                // Add dependencies
                createRepositoriesOrDependencies(
                    project,
                    groovyFile,
                    "dependencies",
                    getDependencies().stream()
                                     .map(d -> String.format("compile '%s:%s:%s'", d.getGroupId(), d.getArtifactId(), d.getVersion()))
                                     .collect(Collectors.toList())
                );

                new ReformatCodeProcessor(file, false).run();
                if (addToDirectory) {
                    PsiDirectory rootDirectoryPsi = PsiManager.getInstance(project).findDirectory(getRootDirectory());
                    if (rootDirectoryPsi != null) {
                        buildGradle.delete(this);

                        rootDirectoryPsi.add(file);
                    }
                }

                GradleBuildSystem.this.buildGradle = getRootDirectory().findChild("build.gradle");
                if (GradleBuildSystem.this.buildGradle == null) {
                    return;
                }

                // Reformat the code to match their code style
                PsiFile newBuildGradlePsi = PsiManager.getInstance(project).findFile(GradleBuildSystem.this.buildGradle);
                if (newBuildGradlePsi != null) {
                    new ReformatCodeProcessor(newBuildGradlePsi, false).run();
                }
            }
        }.execute();
    }

    private void addBuildGradleDependencies(@NotNull Project project, @NotNull String text) {
        // Create the PSI file from the text, but don't write it until we are finished with it
        PsiFile buildGradlePsi = PsiFileFactory.getInstance(project).createFileFromText(GroovyLanguage.INSTANCE, text);

        addBuildGradleDependencies(project, buildGradlePsi, true);
    }

    private void setupDecompWorkspace(@NotNull Project project, @NotNull ProgressIndicator indicator) {
        // We need to setup decomp workspace first
        // We'll use gradle tooling to run it
        GradleConnector connector = GradleConnector.newConnector();
        connector.forProjectDirectory(new File(getRootDirectory().getPath()));
        ProjectConnection connection = connector.connect();
        BuildLauncher launcher = connection.newBuild();

        try {
            Pair<String, Sdk> sdkPair = ExternalSystemJdkUtil.getAvailableJdk(project);
            if (
                sdkPair != null && sdkPair.getSecond() != null && sdkPair.getSecond().getHomePath() != null &&
                !ExternalSystemJdkUtil.USE_INTERNAL_JAVA.equals(sdkPair.getFirst())
            ) {
                launcher.setJavaHome(new File(sdkPair.getSecond().getHomePath()));
            }

            launcher.forTasks("setupDecompWorkspace").setJvmArguments("-Xmx2G").addProgressListener((ProgressListener) progressEvent ->
                indicator.setText(progressEvent.getDescription())
            ).run();
        } finally {
            connection.close();
        }
    }

    private void addRepositories(@NotNull GrClosableBlock block) {
        List<GrClosableBlock> mavenBlocks = getClosableBlocksByName(block, "maven");
        if (mavenBlocks.isEmpty()) {
            return;
        }

        mavenBlocks.forEach(mavenBlock -> {
            BuildRepository repository = new BuildRepository();
            for (PsiElement child : mavenBlock.getChildren()) {
                if (child instanceof GrApplicationStatement) {
                    handleApplicationStatement((GrApplicationStatement) child, repository);
                } else if (child instanceof GrAssignmentExpression) {
                    handleAssignmentExpression((GrAssignmentExpression) child, repository);
                }
            }
            getRepositories().add(repository);
        });
    }

    private void handleApplicationStatement(@NotNull GrApplicationStatement statement, @NotNull BuildRepository repository) {
        GrCommandArgumentList list = statement.getArgumentList();
        if (list.getChildren().length > 0) {
            if (statement.getInvokedExpression().getText().equals("name")) {
                repository.setId(list.getChildren()[0].getText().replaceAll("'", ""));
            } else if (statement.getInvokedExpression().getText().equals("url")) {
                repository.setUrl(list.getChildren()[0].getText().replaceAll("'", ""));
            }
        }
    }

    private void handleAssignmentExpression(@NotNull GrAssignmentExpression expression, @NotNull BuildRepository repository) {
        if (expression.getLValue().getText().equals("name")) {
            if (expression.getRValue() != null) {
                repository.setId(expression.getRValue().getText().replaceAll("'", ""));
            }
        } else if (expression.getLValue().getText().equals("url")) {
            if (expression.getRValue() != null) {
                repository.setUrl(expression.getRValue().getText().replaceAll("'", ""));
            }
        }
    }

    private void setupDirs(@NotNull List<VirtualFile> directories, @NotNull ExternalSourceSet set, @NotNull ExternalSystemSourceType type) {
        if (set.getSources().get(type) != null) {
            set.getSources().get(type).getSrcDirs().forEach(dir -> {
                if (dir.exists()) {
                    directories.add(LocalFileSystem.getInstance().findFileByIoFile(dir));
                }
            });
        }
    }

    private void saveFile(@Nullable VirtualFile file) {
        if (file == null) {
            return;
        }

        Util.runWriteTask(() -> {
            Document document = FileDocumentManager.getInstance().getDocument(file);
            if (document == null) {
                return;
            }

            FileDocumentManager.getInstance().saveDocument(document);
        });
    }
}
