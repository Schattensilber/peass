package de.dagere.peass.dependency.traces;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import de.dagere.peass.dependency.analysis.data.TestCase;
import de.dagere.peass.dependency.persistence.ExecutionData;
import de.dagere.peass.dependency.persistence.VersionStaticSelection;

public class DiffFileGenerator {

   private static final Logger LOG = LogManager.getLogger(DiffFileGenerator.class);

   private final File diffFolder;

   public DiffFileGenerator(final File diffFolder) {
      this.diffFolder = diffFolder;
   }

   public void generateAllDiffs(final String version, final VersionStaticSelection newVersionInfo, final DiffFileGenerator diffGenerator, final TraceFileMapping mapping,
         final ExecutionData executionResult) throws IOException {
      for (TestCase testcase : newVersionInfo.getTests().getTests()) {
         boolean tracesChanged = tracesChanged(testcase, mapping);
         if (tracesChanged) {
            diffGenerator.generateDiffFiles(testcase, mapping);
            executionResult.addCall(version, testcase);
         }
      }
   }

   public boolean tracesChanged(final TestCase testcase, final TraceFileMapping traceFileMap) throws IOException {
      List<File> traceFiles = traceFileMap.getTestcaseMap(testcase);
      if (traceFiles != null) {
         LOG.debug("Trace-Files: {}", traceFiles);
         if (traceFiles.size() > 1) {
            File oldFile = new File(traceFiles.get(0).getAbsolutePath() + OneTraceGenerator.NOCOMMENT);
            File newFile = new File(traceFiles.get(1).getAbsolutePath() + OneTraceGenerator.NOCOMMENT);
            final boolean isDifferent = DiffUtil.isDifferentDiff(oldFile, newFile);
            if (isDifferent) {
               LOG.info("Trace changed.");
               return true;
            } else {
               LOG.info("No change; traces equal.");
               return false;
            }
         } else {
            LOG.info("Traces not existing: {}", testcase);
            return false;
         }
      } else {
         LOG.info("Traces not existing: {}", testcase);
         return false;
      }
   }

   /**
    * Generates a human-analysable diff-file from traces
    *
    * @param testcase Name of the testcase
    * @param diffFolder Goal-folder for the diff
    * @param traceFileMap Map for place where traces are saved
    * @return Whether a change happened
    * @throws IOException If files can't be read of written
    */
   public void generateDiffFiles(final TestCase testcase, final TraceFileMapping traceFileMap) throws IOException {
      final long size = FileUtils.sizeOfDirectory(diffFolder);
      final long sizeInMB = size / (1024 * 1024);
      LOG.debug("Filesize: {} ({})", sizeInMB, size);
      List<File> traceFiles = traceFileMap.getTestcaseMap(testcase);
      createAllDiffs(testcase, traceFiles);
   }

   private void createAllDiffs(final TestCase testcase, final List<File> traceFiles) throws IOException {
      final String testcaseName = testcase.getShortClazz() + "#" + testcase.getMethod();
      DiffUtil.generateDiffFile(new File(diffFolder, testcaseName + ".txt"), traceFiles, "");
      DiffUtil.generateDiffFile(new File(diffFolder, testcaseName + OneTraceGenerator.METHOD), traceFiles, OneTraceGenerator.METHOD);
      DiffUtil.generateDiffFile(new File(diffFolder, testcaseName + OneTraceGenerator.NOCOMMENT), traceFiles,
            OneTraceGenerator.NOCOMMENT);
      DiffUtil.generateDiffFile(new File(diffFolder, testcaseName + OneTraceGenerator.METHOD_EXPANDED), traceFiles,
            OneTraceGenerator.METHOD_EXPANDED);
   }
}
