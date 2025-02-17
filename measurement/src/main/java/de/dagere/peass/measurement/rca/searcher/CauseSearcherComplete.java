package de.dagere.peass.measurement.rca.searcher;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import javax.xml.bind.JAXBException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.dependency.analysis.data.ChangedEntity;
import de.dagere.peass.dependencyprocessors.ViewNotFoundException;
import de.dagere.peass.execution.utils.EnvironmentVariables;
import de.dagere.peass.folders.CauseSearchFolders;
import de.dagere.peass.measurement.rca.CausePersistenceManager;
import de.dagere.peass.measurement.rca.CauseSearcherConfig;
import de.dagere.peass.measurement.rca.CauseTester;
import de.dagere.peass.measurement.rca.analyzer.CompleteTreeAnalyzer;
import de.dagere.peass.measurement.rca.analyzer.TreeAnalyzer;
import de.dagere.peass.measurement.rca.data.CallTreeNode;
import de.dagere.peass.measurement.rca.kieker.BothTreeReader;
import de.dagere.peass.measurement.rca.treeanalysis.AllDifferingDeterminer;
import kieker.analysis.exception.AnalysisConfigurationException;

/**
 * Searches differing nodes in a complete call tree (instead of level-wise analysis)
 * 
 * @author reichelt
 *
 */
public class CauseSearcherComplete extends CauseSearcher {

   private static final Logger LOG = LogManager.getLogger(CauseSearcherComplete.class);

   private final TreeAnalyzerCreator creator;

   public CauseSearcherComplete(final BothTreeReader reader, final CauseSearcherConfig causeSearchConfig, final CauseTester measurer,
         final MeasurementConfig measurementConfig,
         final CauseSearchFolders folders, final EnvironmentVariables env) throws InterruptedException, IOException {
      super(reader, causeSearchConfig, measurer, measurementConfig, folders, env);
      persistenceManager = new CausePersistenceManager(causeSearchConfig, measurementConfig, folders);
      creator = new TreeAnalyzerCreator() {
         @Override
         public TreeAnalyzer getAnalyzer(final BothTreeReader reader, final CauseSearcherConfig config) {
            return new CompleteTreeAnalyzer(reader.getRootVersion(), reader.getRootPredecessor());
         }
      };
   }
   
   public CauseSearcherComplete(final BothTreeReader reader, final CauseSearcherConfig causeSearchConfig, final CauseTester measurer,
         final MeasurementConfig measurementConfig,
         final CauseSearchFolders folders, final TreeAnalyzerCreator creator, final EnvironmentVariables env) throws InterruptedException, IOException {
      super(reader, causeSearchConfig, measurer, measurementConfig, folders, env);
      persistenceManager = new CausePersistenceManager(causeSearchConfig, measurementConfig, folders);
      this.creator = creator;
   }

   @Override
   protected Set<ChangedEntity> searchCause()
         throws IOException, XmlPullParserException, InterruptedException, ViewNotFoundException, AnalysisConfigurationException, JAXBException {
      final TreeAnalyzer analyzer = creator.getAnalyzer(reader, causeSearchConfig);
      final List<CallTreeNode> predecessorNodeList = analyzer.getMeasurementNodesPredecessor();
      final List<CallTreeNode> includableNodes = getIncludableNodes(predecessorNodeList);

      if (includableNodes.isEmpty()) {
         throw new RuntimeException("Tried to analyze empty node list");
      }
      measureDefinedTree(includableNodes);
//      differingNodes.addAll(analyzer.getTreeStructureDiffering());

      return convertToChangedEntitites();
   }

   private List<CallTreeNode> getIncludableNodes(final List<CallTreeNode> predecessorNodeList)
         throws IOException, XmlPullParserException, InterruptedException, ViewNotFoundException, AnalysisConfigurationException, JAXBException {
      final List<CallTreeNode> includableNodes;
      if (causeSearchConfig.useCalibrationRun()) {
         includableNodes = getAnalysableNodes(predecessorNodeList);
      } else {
         includableNodes = predecessorNodeList;
      }

      LOG.debug("Analyzable: {} / {}", includableNodes.size(), predecessorNodeList.size());
      return includableNodes;
   }

   private List<CallTreeNode> getAnalysableNodes(final List<CallTreeNode> predecessorNodeList)
         throws IOException, XmlPullParserException, InterruptedException, ViewNotFoundException, AnalysisConfigurationException, JAXBException {
      final MeasurementConfig config = new MeasurementConfig(1, measurementConfig.getExecutionConfig().getVersion(), measurementConfig.getExecutionConfig().getVersionOld());
      config.setIterations(measurementConfig.getIterations());
      config.setRepetitions(measurementConfig.getRepetitions());
      config.setWarmup(measurementConfig.getWarmup());
      config.setUseKieker(true);
      final CauseTester calibrationMeasurer = new CauseTester(folders, config, causeSearchConfig, env);
      final AllDifferingDeterminer calibrationRunner = new AllDifferingDeterminer(predecessorNodeList, causeSearchConfig, config);
      calibrationMeasurer.measureVersion(predecessorNodeList);
      final List<CallTreeNode> includableByMinTime = calibrationRunner.getIncludableNodes();
      return includableByMinTime;
   }
}
