package de.dagere.peass.measurement.rca.treeanalysis;

import java.util.LinkedList;
import java.util.List;

import org.apache.commons.math3.stat.descriptive.SummaryStatistics;

import de.dagere.peass.config.MeasurementConfig;
import de.dagere.peass.measurement.rca.CauseSearcherConfig;
import de.dagere.peass.measurement.rca.data.CallTreeNode;

/**
 * Determines the differing nodes analyzing the whole tree at once
 * 
 * @author reichelt
 *
 */
public class AllDifferingDeterminer extends DifferentNodeDeterminer {

   public AllDifferingDeterminer(final List<CallTreeNode> needToMeasurePredecessor, final CauseSearcherConfig causeSearchConfig,
         final MeasurementConfig measurementConfig) {
      super(causeSearchConfig, measurementConfig);
//      this.needToMeasureCurrent = needToMeasureCurrent;
      this.measurePredecessor = needToMeasurePredecessor;
   }
   
   @Override
   public void calculateDiffering() {
      super.calculateDiffering();
   }

   public List<CallTreeNode> getIncludableNodes() {
      final List<CallTreeNode> includeable = new LinkedList<CallTreeNode>();
      for (final CallTreeNode node : measurePredecessor) {
         final SummaryStatistics statistics = node.getStatistics(measurementConfig.getExecutionConfig().getVersion());
         final SummaryStatistics statisticsOld = node.getStatistics(measurementConfig.getExecutionConfig().getVersionOld());
         if (statistics.getMean() > causeSearchConfig.getMinTime() &&
               statisticsOld.getMean() > causeSearchConfig.getMinTime()) {
            includeable.add(node);
         }
      }
      return includeable;

   }
}