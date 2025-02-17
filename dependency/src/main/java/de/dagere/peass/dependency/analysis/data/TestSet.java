/**
 *     This file is part of PerAn.
 *
 *     PerAn is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     PerAn is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with PerAn.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.dagere.peass.dependency.analysis.data;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

import de.dagere.peass.dependency.analysis.data.deserializer.TestcaseKeyDeserializer;

/**
 * Represents a set of tests which are executed for one version by its class and its list of methods.
 * 
 * @author reichelt
 *
 */
public class TestSet {

   private static final Logger LOG = LogManager.getLogger(TestSet.class);

   

   @JsonInclude(JsonInclude.Include.NON_EMPTY)
   @JsonDeserialize(keyUsing = TestcaseKeyDeserializer.class, contentAs = TreeSet.class, as = TreeMap.class)
   private final Map<TestCase, Set<String>> testcases = new TreeMap<>();
   private String predecessor;

   public TestSet() {
   }

   public TestSet(final String testcase) {
      addTest(new TestCase(testcase));
   }

   public TestSet(final TestCase testcase) {
      addTest(testcase);
   }

   public TestSet(final List<String> testcases) {
      for (String testcase : testcases) {
         addTest(new TestCase(testcase));
      }
   }

   @JsonIgnore
   public void addTest(final TestCase classname) {
      final TestCase entity = new TestCase(classname.getClazz(), null, classname.getModule());
      addTest(entity, classname.getMethodWithParams());
   }

   /**
    * Adds a test to the TestSet. If the method is null, and no further method is added, the TestSet contains the all methods of the test; if one method is added, only the method
    * (and perhaps future added methods) are included.
    * 
    * @param classname
    * @param methodname
    */
   public void addTest(final TestCase classname, final String methodname) {
      if (classname.getMethod() != null && classname.getMethod() != "") {
         throw new RuntimeException("A testset should only get Changed Entities with empty method, but was " + classname.getMethod());
      }
      Set<String> methods = testcases.get(classname);
      if (methods == null) {
         methods = new TreeSet<>();
         testcases.put(classname.copy(), methods);
      }
      if (methodname != null) {
         final String internalizedMethodName = methodname.intern();
         if (!methods.contains(internalizedMethodName)) {
            methods.add(internalizedMethodName);
         }
      }
   }

   @JsonIgnore
   public void addTestSet(final TestSet testSet) {
      for (final Map.Entry<TestCase, Set<String>> newTestEntry : testSet.entrySet()) {
         Set<String> methods = testcases.get(newTestEntry.getKey());
         if (methods == null) {
            methods = new TreeSet<>();
            methods.addAll(newTestEntry.getValue());
            testcases.put(newTestEntry.getKey().copy(), methods);
         } else {
            if (newTestEntry.getValue().size() != 0 && methods.size() != 0) {
               methods.addAll(newTestEntry.getValue());
            } else {
               // If List is empty, all methods are changed -> Should be
               // remembered
               methods.clear();
            }
         }
      }
   }

   @JsonIgnore
   public Set<Entry<TestCase, Set<String>>> entrySet() {
      return testcases.entrySet();
   }

   @JsonIgnore
   public int classCount() {
      return testcases.size();
   }

   @JsonIgnore
   public Set<TestCase> getClasses() {
      return testcases.keySet();
   }

   @JsonIgnore
   public Set<TestCase> getTests() {
      final Set<TestCase> testcases = new LinkedHashSet<>();
      for (final Entry<TestCase, Set<String>> classTests : getTestcases().entrySet()) {
         if (classTests.getValue().size() > 0) {
            for (final String method : classTests.getValue()) {
               final TestCase testcase = new TestCase(classTests.getKey().getClazz(), method, classTests.getKey().getModule());
               testcases.add(testcase);
            }
         } else {
            testcases.add(new TestCase(classTests.getKey().getClazz(), null, classTests.getKey().getModule()));
         }
      }
      return testcases;
   }

   public Map<TestCase, Set<String>> getTestcases() {
      return testcases;
   }

   public void removeTest(final TestCase testClassName) {
      if (testClassName.getMethod() != null && testClassName.getMethod() != "") {
         throw new RuntimeException("Testset class removal should only be done with empty method of ChangedEntity!");
      }
      testcases.remove(testClassName);
   }

   @JsonIgnore
   public void removeTest(final TestCase testClassName, final String testMethodName) {
      if (testClassName.getMethod() != null && testClassName.getMethod() != "") {
         throw new RuntimeException("Testset class removal should only be done with empty method of ChangedEntity!");
      }
      final Set<String> testMethods = testcases.get(testClassName);
      if (testMethods != null) {
         removeMethod(testClassName, testMethodName, testMethods);
      } else {
         TestCase other = null;
         for (TestCase entity : testcases.keySet()) {
            if (entity.getClazz().equals(testClassName.getClazz())) {
               other = entity;
            }
         }
         if (other != null) {
            LOG.warn("{} not found - found {} instead. Modules should be saved in ChangedEntity-instances!", testClassName, other);
            removeMethod(other, testMethodName, testcases.get(other));
         } else {
            LOG.error("Testclass " + testClassName + " missing");
         }

      }
   }

   private void removeMethod(final TestCase testClassName, final String testMethodName, final Set<String> testMethods) {
      LOG.debug("Removing: " + testClassName + "#" + testMethodName);
      if (!testMethods.remove(testMethodName)) {
         LOG.error("Problem: " + testMethodName + " can not be removed.");
         // throw new RuntimeException("Test " + testMethodName + " was not in TestSet!");
      }
      if (testMethods.size() == 0) {
         testcases.remove(testClassName);
      }
   }

   @Override
   public String toString() {
      return testcases.toString();
   }

   @JsonIgnore
   public Set<String> getMethods(final TestCase clazz) {
      return testcases.get(clazz);
   }

   @JsonInclude(Include.NON_EMPTY)
   public String getPredecessor() {
      return predecessor;
   }

   public void setPredecessor(final String predecessor) {
      this.predecessor = predecessor;
   }
}
