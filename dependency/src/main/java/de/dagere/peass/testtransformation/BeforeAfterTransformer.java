package de.dagere.peass.testtransformation;

import java.util.List;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;

public class BeforeAfterTransformer {

   public static void transformWithMeasurement(final ClassOrInterfaceDeclaration clazz) {
      List<MethodDeclaration> beforeEachMethods = TestMethodFinder.findBeforeEachMethods(clazz);
      transformMethodAnnotations(beforeEachMethods, "de.dagere.kopeme.junit.rule.annotations.BeforeWithMeasurement", 1);
      
      List<MethodDeclaration> beforeAllMethods = TestMethodFinder.findBeforeAllMethods(clazz);
      transformMethodAnnotations(beforeAllMethods, "de.dagere.kopeme.junit.rule.annotations.BeforeWithMeasurement", 2);
      
      List<MethodDeclaration> afterEachMethods = TestMethodFinder.findAfterEachMethods(clazz);
      transformMethodAnnotations(afterEachMethods, "de.dagere.kopeme.junit.rule.annotations.AfterWithMeasurement", 1);
      
      List<MethodDeclaration> afterAllMethods = TestMethodFinder.findAfterAllMethods(clazz);
      transformMethodAnnotations(afterAllMethods, "de.dagere.kopeme.junit.rule.annotations.AfterWithMeasurement", 2);
   }

   public static void transformBefore(final ClassOrInterfaceDeclaration clazz) {
      List<MethodDeclaration> beforeEachMethods = TestMethodFinder.findBeforeEachMethods(clazz);
      transformMethodAnnotations(beforeEachMethods, "de.dagere.kopeme.junit.rule.annotations.BeforeNoMeasurement", 1);
      
      List<MethodDeclaration> beforeAllMethods = TestMethodFinder.findBeforeAllMethods(clazz);
      transformMethodAnnotations(beforeAllMethods, "de.dagere.kopeme.junit.rule.annotations.BeforeNoMeasurement", 2);
   }

   public static void transformAfter(final ClassOrInterfaceDeclaration clazz) {
      List<MethodDeclaration> beforeEachMethods = TestMethodFinder.findAfterEachMethods(clazz);
      transformMethodAnnotations(beforeEachMethods, "de.dagere.kopeme.junit.rule.annotations.AfterNoMeasurement", 1);
      
      List<MethodDeclaration> beforeAllMethods = TestMethodFinder.findAfterAllMethods(clazz);
      transformMethodAnnotations(beforeAllMethods, "de.dagere.kopeme.junit.rule.annotations.AfterNoMeasurement", 2);
   }

   private static void transformMethodAnnotations(final List<MethodDeclaration> beforeMethods, final String name, final int priority) {
      for (MethodDeclaration method : beforeMethods) {
         final NormalAnnotationExpr beforeNoMeasurementAnnotation = new NormalAnnotationExpr();

         beforeNoMeasurementAnnotation.setName(name);
         method.setAnnotation(0, beforeNoMeasurementAnnotation);
         
         beforeNoMeasurementAnnotation.addPair("priority", Integer.toString(priority));

      }
   }
}
