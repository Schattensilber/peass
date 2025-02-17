package de.dagere.peass.measurement.rca.kieker;

import java.io.File;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import kieker.model.system.model.Operation;

public class KiekerPatternConverter {

   private static final Logger LOG = LogManager.getLogger(KiekerPatternConverter.class);

   /**
    * Fixes parameters, i.e. removes spaces, that might be introduced by kieker.
    * 
    * @param kiekerCall Kieker call that should be fixed
    * @return fixed call
    */
   public static String fixParameters(final String kiekerCall) {
      LOG.info("Fixing: {}", kiekerCall);
      final int parametersBegin = kiekerCall.indexOf('(');
      if (parametersBegin != -1) {
         final String namePart = kiekerCall.substring(0, parametersBegin);
         String parameterPart = kiekerCall.substring(parametersBegin);
         parameterPart = parameterPart.replace(" ", "");
         final String result = namePart + parameterPart;
         return result;
      } else {
         return kiekerCall;
      }
   }

   public static String getCall(final String kiekerPattern) {
      if (!kiekerPattern.contains("(") || !kiekerPattern.contains(")")) {
         throw new RuntimeException(kiekerPattern + " is supposed to contain ( and )");
      }
      String beforeParameters = kiekerPattern.substring(0, kiekerPattern.indexOf('('));
      String method = beforeParameters.substring(beforeParameters.lastIndexOf('.') + 1);
      String clazz = beforeParameters.substring(beforeParameters.lastIndexOf(' ') + 1, beforeParameters.lastIndexOf('.'));
      return clazz + "#" + method;
   }

   public static String addNewIfRequired(String kiekerPattern) {
      if (kiekerPattern.contains("<init>")) {
         int beforeReturnType = kiekerPattern.lastIndexOf(" ");
         if (beforeReturnType == -1) {
            kiekerPattern = "new " + kiekerPattern;
         } else {
            String modifiers = kiekerPattern.substring(0, beforeReturnType);
            String remainder = kiekerPattern.substring(beforeReturnType + 1);
            String returnType = modifiers.lastIndexOf(" ") != -1 ? modifiers.substring(modifiers.lastIndexOf(" ") + 1) : modifiers;
            if (!"new".equals(returnType)) {
               kiekerPattern = modifiers + " new " + remainder;
            }
         }
      }
      return kiekerPattern;
   }

   /**
    * Expects a call in the form 'de.test.Class#method(int,String)'
    * 
    * @param kiekerCall
    * @return
    */
   public static String getKiekerPattern(final String kiekerCall) {
      final int parametersBegin = kiekerCall.indexOf('(');
      String namePart = kiekerCall.substring(0, parametersBegin);
      namePart = addNew(namePart);

      String parameterPart = kiekerCall.substring(parametersBegin);
      parameterPart = parameterPart.replace(" ", "");
      final String result = namePart + parameterPart;
      return result;
   }

   private static String addNew(String kiekerCall) {
      if (kiekerCall.contains("<init>")) {
         final String[] splitted = kiekerCall.split(" ");
         String repaired;
         if (splitted[0].equals("public") || splitted[0].equals("protected") || splitted[0].equals("private")) {
            if (!splitted[1].equals("new")) {
               repaired = splitted[0] + " new ";
               for (int i = 1; i < splitted.length; i++) {
                  repaired += splitted[i] + " ";
               }
               repaired = repaired.substring(0, repaired.length() - 1);
            } else {
               repaired = kiekerCall;
            }
         } else {
            if (!splitted[0].equals("new")) {
               repaired = "new " + kiekerCall;
            } else {
               repaired = kiekerCall;
            }
         }
         kiekerCall = repaired;
      }
      return kiekerCall;
   }

   public static String getKiekerPattern(final Operation operation) {
      final StringBuilder signatureBuilder = new StringBuilder();
      boolean containsNew = false;
      for (final String modifier : operation.getSignature().getModifier()) {
         signatureBuilder.append(modifier).append(' ');
         if ("new".equals(modifier)) {
            containsNew = true;
         }
      }
      if (operation.getSignature().hasReturnType()) {
         signatureBuilder.append(operation.getSignature().getReturnType())
               .append(' ');
      } else {
         // Only add new if it is not already present (since source instrumentation adds new itself)
         if (!containsNew) {
            signatureBuilder.append("new").append(' ');
         }

      }
      signatureBuilder.append(operation.getComponentType().getFullQualifiedName())
            .append('.')
            .append(operation.getSignature().getName())
            .append('(');

      boolean first = true;
      for (final String paramType : operation.getSignature().getParamTypeList()) {
         if (!first) {
            signatureBuilder.append(',');
         } else {
            first = false;
         }
         signatureBuilder.append(paramType);
      }
      signatureBuilder.append(')');

      String signature = signatureBuilder.toString();
      return signature;
   }

   public static String getFileNameStart(final String kiekerPattern) {
      final String separator = File.separator;
      String fileNameStart = convertPattern(kiekerPattern, separator);
      return fileNameStart;
   }

   public static String getKey(final String kiekerPattern) {
      final String separator = ".";
      String fileNameStart = convertPattern(kiekerPattern, separator);
      return fileNameStart;
   }

   private static String convertPattern(final String kiekerPattern, final String separator) {
      final String call = kiekerPattern.substring(kiekerPattern.lastIndexOf(' ') + 1, kiekerPattern.lastIndexOf('('));
      final int dotIndex = call.lastIndexOf(".");
      String method = call.substring(dotIndex + 1);
      String clazz = call.substring(0, dotIndex);

      String parameterString = getParameterKeyString(kiekerPattern);

      String fileNameStart = clazz + separator + method + "_" + parameterString;
      return fileNameStart;
   }

   public static String getParameterKeyString(final String kiekerPattern) {
      String parameters = kiekerPattern.substring(kiekerPattern.lastIndexOf('(') + 1, kiekerPattern.length() - 1);
      String parameterString = "";
      for (String parameter : parameters.split(",")) {
         final int index = parameter.lastIndexOf('.');
         parameterString += (index != -1 ? parameter.substring(index + 1) : parameter) + "_";
      }
      parameterString = parameterString.substring(0, parameterString.length() - 1);
      return parameterString;
   }
}
