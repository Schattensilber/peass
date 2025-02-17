package de.dagere.peass;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.Callable;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.analysis.all.ReadAllProperties;
import de.dagere.peass.analysis.all.RepoFolders;
import de.dagere.peass.analysis.changes.Change;
import de.dagere.peass.analysis.changes.Changes;
import de.dagere.peass.analysis.changes.ProjectChanges;
import de.dagere.peass.analysis.properties.ChangeProperties;
import de.dagere.peass.analysis.properties.ChangeProperty;
import de.dagere.peass.analysis.properties.PropertyReadHelper;
import de.dagere.peass.analysis.properties.PropertyReader;
import de.dagere.peass.analysis.properties.VersionChangeProperties;
import de.dagere.peass.config.ExecutionConfig;
import de.dagere.peass.dependency.analysis.data.ChangedEntity;
import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.analysis.data.TestSet;
import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.persistence.SelectedTests;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.measurement.dataloading.VersionSorter;
import de.dagere.peass.utils.Constants;
import de.dagere.peass.vcs.GitUtils;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * Reads a changes.json which contains all changes that happened in a project and determines properties of the changes.
 * 
 * @deprecated property determination is now done directly when reading dependencies (so classes need only to be passed once)
 * 
 * @author reichelt
 *
 */
@Deprecated
@Command(description = "Reads the properties of given changes", name = "readproperties")
public class ReadProperties implements Callable<Void> {

   private static final Logger LOG = LogManager.getLogger(ReadProperties.class);

   @Option(names = { "-staticSelectionFile", "--staticSelectionFile" }, description = "Path to the static selection file")
   protected File staticSelectionFile;

   @Option(names = { "-executionfile", "--executionfile" }, description = "Path to the executionfile")
   protected File executionFile;

   @Option(names = { "-changefile", "--changefile" }, description = "Path to the changefile")
   protected File changefile;
   
   @Option(names = { "-viewfolder", "--viewfolder" }, description = "Path to the viewfolder")
   protected File viewfolder;

   @Option(names = { "-folder", "--folder" }, description = "Folder of the project that should be analyzed", required = true)
   protected File projectFolder;

   @Option(names = { "-out", "--out" }, description = "Path for saving the propertyfile")
   protected File out;

   public ReadProperties() {

   }

   public ReadProperties(final File resultFile) {
      out = resultFile;
   }

   public static void main(final String[] args) throws JsonParseException, JsonMappingException, IOException, JAXBException {
      final CommandLine commandLine = new CommandLine(new ReadProperties());
      System.exit(commandLine.execute(args));
   }

   @Override
   public Void call() throws Exception {
      final RepoFolders folders = new RepoFolders();
      final String projectName = projectFolder.getName();
      SelectedTests selectedTests = VersionSorter.getSelectedTests(staticSelectionFile, executionFile, folders.getDependencyFile(projectName));

      if (!projectFolder.exists()) {
         GitUtils.downloadProject(selectedTests.getUrl(), projectFolder);
      }
      
      System.out.println("Read all: " + ReadAllProperties.readAll);
      if (ReadAllProperties.readAll) {
         final ExecutionData changedTests = executionFile != null ? Constants.OBJECTMAPPER.readValue(executionFile, ExecutionData.class) : folders.getExecutionData(projectName);
         final File resultFile = new File("results" + File.separator + projectName + File.separator + "properties_alltests.json");
         out = resultFile;
         ResultsFolders resultsFolders = new ResultsFolders(out, projectName);
         final PropertyReader reader = new PropertyReader(resultsFolders, projectFolder, changedTests, new ExecutionConfig());
         reader.readAllTestsProperties();
      } else {

         final File changefile;
         if (this.changefile != null) {
            changefile = this.changefile;
         } else {
            changefile = folders.getChangeFile(projectName);
         }

         // final File changefile = new File(commandLine.getOptionValue(OptionConstants.CHANGEFILE.getName()));

         if (out == null) {
            out = folders.getProjectPropertyFile(projectName);
            if (!out.getParentFile().exists()) {
               out.getParentFile().mkdirs();
            }
         }

         if (!changefile.exists()) {
            LOG.error("Changefile {} needs to exist.", changefile);
            System.exit(1);
         }

         if (!viewfolder.exists()) {
            LOG.error("ViewFolder {} needs to exist.", viewfolder);
            System.exit(1);
         }

         StaticTestSelection dependencies = Constants.OBJECTMAPPER.readValue(staticSelectionFile, StaticTestSelection.class);
         
         readChangeProperties(changefile, projectFolder, viewfolder, new ExecutionData(dependencies));
      }
      return null;
   }

   public void readChangeProperties(final File changefile, final File projectFolder, final File viewFolder, final ExecutionData changedTests)
         throws IOException, JsonParseException, JsonMappingException, JsonGenerationException {
      final File resultCSV = new File(out.getParentFile(), projectFolder.getName() + ".csv");

      try (BufferedWriter csvWriter = new BufferedWriter(new FileWriter(resultCSV))) {
         writeCSVHeadline(csvWriter);
         final VersionChangeProperties versionProperties = new VersionChangeProperties();

         final ProjectChanges changes = Constants.OBJECTMAPPER.readValue(changefile, ProjectChanges.class);

         int versionCount = 0, testcaseCount = 0;
         for (final Entry<String, Changes> versionChanges : changes.getVersionChanges().entrySet()) {
            final String version = versionChanges.getKey();
            final TestSet tests = changedTests.getVersions().get(version);
            //
            final String predecessor = tests != null ? tests.getPredecessor() : version + "~1";
            
            testcaseCount += detectVersionProperty(projectFolder, viewFolder, csvWriter, versionProperties, versionChanges, predecessor, changedTests);
            if (tests == null) {
               LOG.error("Version not contained in runfile: " + version);
            }
            versionCount++;
         }
         // writeOnlySource(versionProperties, ProjectChanges.getOldChanges());
         System.out.println("Analyzed: " + testcaseCount + " testcases in " + versionCount + " versions");
      }
   }

   private int detectVersionProperty(final File projectFolder, final File viewFolder, final BufferedWriter csvWriter,
         final VersionChangeProperties versionProperties,
         final Entry<String, Changes> versionChanges, final String predecessor, final ExecutionData data) throws IOException, JsonGenerationException, JsonMappingException {
      final File methodFolder = new File(out.getParentFile(), "methods");
      methodFolder.mkdirs();
      final String version = versionChanges.getKey();
      final ChangeProperties changeProperties = new ChangeProperties();
      changeProperties.setCommitText(GitUtils.getCommitText(projectFolder, version));
      changeProperties.setCommitter(GitUtils.getCommitter(projectFolder, version));
      versionProperties.getVersions().put(version, changeProperties);
      int count = 0;
      for (final Entry<String, List<Change>> changes : versionChanges.getValue().getTestcaseChanges().entrySet()) {
         final String testclazz = changes.getKey();
         String module = null;
         for (TestCase test : data.getVersions().get(versionChanges.getKey()).getTests()) {
            if (test.getClazz().equals(testclazz)) {
               module = test.getModule();
            }
         }
         final List<ChangeProperty> properties = new LinkedList<>();
         changeProperties.getProperties().put(testclazz, properties);
         for (final Change testcaseChange : changes.getValue()) {
            ExecutionConfig config = new ExecutionConfig();
            config.setVersion(version);
            config.setVersionOld(predecessor);
            ChangedEntity entity = new ChangedEntity(testclazz, module);
            final PropertyReadHelper reader = new PropertyReadHelper(config, entity, testcaseChange, projectFolder, viewFolder,
                  methodFolder, null);
            final ChangeProperty currentProperty = reader.read();
            // if (currentProperty != null) {
            properties.add(currentProperty);
            Constants.OBJECTMAPPER.writeValue(out, versionProperties);
            writeCSVLine(csvWriter, currentProperty, projectFolder.getName());
            // }

            count++;
         }
      }
      return count;
   }

   public static VersionChangeProperties readVersionProperties(final ProjectChanges knowledge, final File versionFile) {
      final VersionChangeProperties versionProperties = new VersionChangeProperties();
      try {
         final VersionChangeProperties allProperties = Constants.OBJECTMAPPER.readValue(versionFile, VersionChangeProperties.class);
         for (final Entry<String, Changes> versionChanges : knowledge.getVersionChanges().entrySet()) {
            final String version = versionChanges.getKey();
            final ChangeProperties allProps = allProperties.getVersions().get(version);
            if (allProps != null) {
               final ChangeProperties changeProperties = new ChangeProperties();
               changeProperties.setCommitText(allProps.getCommitText());
               changeProperties.setCommitter(allProps.getCommitText());
               versionProperties.getVersions().put(version, changeProperties);
               for (final Entry<String, List<Change>> changes : versionChanges.getValue().getTestcaseChanges().entrySet()) {
                  final String testclazz = changes.getKey();
                  final List<ChangeProperty> properties = new LinkedList<>();
                  final List<ChangeProperty> oldTestcaseProperties = allProps.getProperties().get(changes.getKey());
                  if (oldTestcaseProperties != null) {
                     changeProperties.getProperties().put(testclazz, properties);
                     for (final Change change : changes.getValue()) {
                        for (final ChangeProperty prop : oldTestcaseProperties) {
                           if (prop.getMethod().equals(change.getMethod())) {
                              properties.add(prop);
                           }
                        }
                     }
                  }
               }
            }
         }

      } catch (final IOException e) {
         e.printStackTrace();
      }
      return versionProperties;
   }

   public static void writeCSVHeadline(final BufferedWriter csvWriter) throws IOException {
      String line = "#Diff" + ";" + "project;" + "method" + ";" + "intensity" + ";" + "testChange;" + "traceChange" + ";";
      line += "calls" + ";" + "callsOld" + ";" + "oldTime" + ";";
      for (final String keyword : PropertyReadHelper.keywords) {
         line += keyword + "Added;" + keyword + "Removed;";
      }
      csvWriter.write(line.substring(0, line.length() - 1) + "\n");
      csvWriter.flush();

      String line2 = "CREATE TABLE properties(Diff VARCHAR(250),  method VARCHAR(250),  intensity DOUBLE, testChange VARCHAR(50), traceChange VARCHAR(50), ";
      line2 += "calls INT, callsOld INT, oldTime INT,";
      for (final String keyword : PropertyReadHelper.keywords) {
         line2 += keyword.replace('.', '_') + "Added INT," + keyword.replace('.', '_') + "Removed INT,";
      }
      System.out.println(line2.substring(0, line2.length() - 1) + ")");
   }

   private static void writeCSVLine(final BufferedWriter csvWriter, final ChangeProperty currentProperty, final String project) throws IOException {
      String testChange;
      if (currentProperty.isAffectsSource() && currentProperty.isAffectsTestSource()) {
         testChange = "AFFECTSBOTH";
      } else if (currentProperty.isAffectsSource()) {
         testChange = "AFFECTSSOURCE";
      } else if (currentProperty.isAffectsTestSource()) {
         testChange = "AFFECTSTEST";
      } else {
         testChange = "NONE";
      }

      String line = currentProperty.getDiff() + ";" + project + ";" + currentProperty.getMethod() + ";" + currentProperty.getChangePercent() + ";"
            + testChange + ";"
            + currentProperty.getTraceChangeType() + ";";
      line += currentProperty.getCalls() + ";" + currentProperty.getCallsOld() + ";" + currentProperty.getOldTime() + ";";
      for (final String keyword : PropertyReadHelper.keywords) {
         Integer value = currentProperty.getAddedMap().get(keyword);
         if (value == null)
            value = 0;
         line += value + ";";
         Integer valueRemove = currentProperty.getRemovedMap().get(keyword);
         if (valueRemove == null)
            valueRemove = 0;
         line += valueRemove + ";";
      }
      csvWriter.write(line.substring(0, line.length() - 1) + "\n");
      csvWriter.flush();
   }
}
