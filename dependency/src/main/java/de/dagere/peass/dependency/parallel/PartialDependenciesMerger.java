package de.dagere.peass.dependency.parallel;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

import de.dagere.peass.dependency.persistence.StaticTestSelection;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.reader.DependencyParallelReader;
import de.dagere.peass.dependency.reader.DependencyReaderUtil;
import de.dagere.peass.dependencyprocessors.VersionComparator;
import de.dagere.peass.folders.ResultsFolders;
import de.dagere.peass.utils.Constants;

public class PartialDependenciesMerger {

   private static final Logger LOG = LogManager.getLogger(DependencyParallelReader.class);

   private PartialDependenciesMerger() {

   }

   public static StaticTestSelection mergeVersions(final File out, final File[] partFiles) throws IOException, JsonGenerationException, JsonMappingException {
      final List<StaticTestSelection> deps = readDependencies(partFiles);
      StaticTestSelection merged = mergeDependencies(deps);

      Constants.OBJECTMAPPER.writeValue(out, merged);
      return merged;
   }

   public static StaticTestSelection mergeVersions(final File out, final ResultsFolders[] partFolders) throws IOException, JsonGenerationException, JsonMappingException {
      File[] partFiles = new File[partFolders.length];
      for (int i = 0; i < partFolders.length; i++) {
         partFiles[i] = partFolders[i].getStaticTestSelectionFile();
      }
      return mergeVersions(out, partFiles);
   }

   static List<StaticTestSelection> readDependencies(final File[] partFiles) {
      final List<StaticTestSelection> deps = new LinkedList<>();
      for (int i = 0; i < partFiles.length; i++) {
         try {
            LOG.debug("Reading: {}", partFiles[i]);
            final StaticTestSelection currentDependencies = Constants.OBJECTMAPPER.readValue(partFiles[i], StaticTestSelection.class);
            deps.add(currentDependencies);
            LOG.debug("Size: {}", deps.get(deps.size() - 1).getVersions().size());
         } catch (final IOException e) {
            e.printStackTrace();
         }
      }
      return deps;
   }

   public static StaticTestSelection mergeDependencies(final List<StaticTestSelection> deps) {
      LOG.debug("Sorting {} dependencies", deps.size());
      deps.sort(new Comparator<StaticTestSelection>() {
         @Override
         public int compare(final StaticTestSelection o1, final StaticTestSelection o2) {
            final int indexOf = VersionComparator.getVersionIndex(o1.getInitialversion().getVersion());
            final int indexOf2 = VersionComparator.getVersionIndex(o2.getInitialversion().getVersion());
            return indexOf - indexOf2;
         }
      });
      StaticTestSelection merged;
      if (deps.size() > 0) {
         merged = deps.get(0);
         if (deps.size() > 1) {
            for (int i = 1; i < deps.size(); i++) {
               final StaticTestSelection newMergeDependencies = deps.get(i);
               LOG.debug("Merge: {} Vals: {}", i, newMergeDependencies.getVersionNames());
               if (newMergeDependencies != null) {
                  merged = DependencyReaderUtil.mergeDependencies(merged, newMergeDependencies);
               }
            }
         }
      } else {
         merged = new StaticTestSelection();
      }
      return merged;
   }

   public static ExecutionData mergeExecutiondata(final List<ExecutionData> executionData) {
      ExecutionData merged = new ExecutionData();
      for (ExecutionData data : executionData) {
         if (merged.getUrl() == null && data.getUrl() != null) {
            merged.setUrl(data.getUrl());
         }
         merged.getVersions().putAll(data.getVersions());
      }
      return merged;
   }

   public static ExecutionData mergeExecutions(final ResultsFolders mergedOut, final ResultsFolders[] outFiles) throws JsonParseException, JsonMappingException, IOException {
      List<File> executionOutFiles = new LinkedList<>();
      List<File> coverageSelectionOutFiles = new LinkedList<>();
      for (ResultsFolders resultFolder : outFiles) {
         if (resultFolder != null) {
            if (resultFolder.getTraceTestSelectionFile().exists()) {
               executionOutFiles.add(resultFolder.getTraceTestSelectionFile());
            }
            if (resultFolder.getCoverageSelectionFile() != null && resultFolder.getCoverageSelectionFile().exists()) {
               coverageSelectionOutFiles.add(resultFolder.getCoverageSelectionFile());
            }

         }
      }
      ExecutionData mergedExecutions = mergeExecutionFiles(executionOutFiles);
      Constants.OBJECTMAPPER.writeValue(mergedOut.getTraceTestSelectionFile(), mergedExecutions);

      if (coverageSelectionOutFiles.size() > 0) {
         ExecutionData mergedCoverage = mergeExecutionFiles(coverageSelectionOutFiles);
         Constants.OBJECTMAPPER.writeValue(mergedOut.getCoverageSelectionFile(), mergedCoverage);
      }
      return mergedExecutions;
   }

   private static ExecutionData mergeExecutionFiles(final List<File> executionOutFiles) throws IOException, JsonParseException, JsonMappingException {
      List<ExecutionData> executionData = new LinkedList<>();
      for (File file : executionOutFiles) {
         ExecutionData currentData = Constants.OBJECTMAPPER.readValue(file, ExecutionData.class);
         executionData.add(currentData);
      }
      ExecutionData merged = mergeExecutiondata(executionData);
      return merged;
   }
}
