/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.capability.xml;

import static com.google.common.collect.ImmutableSet.copyOf;
import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mule.runtime.api.dsl.DslResolvingContext.getDefault;
import static org.mule.runtime.core.config.MuleManifest.getProductVersion;
import static org.mule.runtime.core.util.IOUtils.getResourceAsString;
import static org.mule.runtime.module.extension.internal.loader.java.AbstractJavaExtensionModelLoader.TYPE_PROPERTY_NAME;
import static org.mule.runtime.module.extension.internal.loader.java.AbstractJavaExtensionModelLoader.VERSION;
import org.mule.runtime.api.dsl.DslResolvingContext;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.type.TypeCatalog;
import org.mule.runtime.core.api.registry.ServiceRegistry;
import org.mule.runtime.extension.api.loader.DeclarationEnricher;
import org.mule.runtime.extension.api.loader.ExtensionModelLoader;
import org.mule.runtime.module.extension.internal.capability.xml.schema.SchemaGenerator;
import org.mule.runtime.module.extension.internal.loader.enricher.JavaXmlDeclarationEnricher;
import org.mule.runtime.module.extension.internal.loader.java.DefaultJavaExtensionModelLoader;
import org.mule.runtime.module.extension.internal.resources.MuleExtensionModelProvider;
import org.mule.runtime.module.extension.soap.internal.loader.SoapExtensionModelLoader;
import org.mule.tck.junit4.AbstractMuleTestCase;
import org.mule.tck.size.SmallTest;
import org.mule.test.oauth.TestOAuthExtension;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@SmallTest
@RunWith(Parameterized.class)
public class SchemaGeneratorTestCase extends AbstractMuleTestCase {

  static final Map<String, ExtensionModel> extensionModels = new HashMap<>();

  private static ExtensionModelLoader javaLoader = new DefaultJavaExtensionModelLoader();
  private static ExtensionModelLoader soapLoader = new SoapExtensionModelLoader();

  @Parameterized.Parameter(0)
  public ExtensionModel extensionUnderTest;

  @Parameterized.Parameter(1)
  public String expectedXSD;

  private SchemaGenerator generator;
  private String expectedSchema;


  @Parameterized.Parameters(name = "{1}")
  public static Collection<Object[]> data() {
    final ClassLoader classLoader = SchemaGeneratorTestCase.class.getClassLoader();
    final ServiceRegistry serviceRegistry = mock(ServiceRegistry.class);
    when(serviceRegistry.lookupProviders(DeclarationEnricher.class, classLoader))
        .thenReturn(asList(new JavaXmlDeclarationEnricher()));

    final List<SchemaGeneratorTestUnit> extensions = Arrays.asList(
                                                                   // new SchemaGeneratorTestUnit(javaLoader, MapConnector.class,
                                                                   //                             "map.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader, ListConnector.class,
                                                                   //                             "list.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader, TestConnector.class,
                                                                   //                             "basic.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             StringListConnector.class,
                                                                   //                             "string-list.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             GlobalPojoConnector.class,
                                                                   //                             "global-pojo.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             GlobalInnerPojoConnector.class,
                                                                   //                             "global-inner-pojo.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader, VeganExtension.class,
                                                                   //                             "vegan.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             PetStoreConnector.class,
                                                                   //                             "petstore.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             MetadataExtension.class,
                                                                   //                             "metadata.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             HeisenbergExtension.class,
                                                                   //                             "heisenberg.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             TransactionalExtension.class,
                                                                   //                             "tx-ext.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             SubTypesMappingConnector.class,
                                                                   //                             "subtypes.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader, MarvelExtension.class,
                                                                   //                             "marvel.xsd"),
                                                                   // new SchemaGeneratorTestUnit(soapLoader,
                                                                   //                             FootballSoapExtension.class,
                                                                   //                             "soap.xsd"),
                                                                   // new SchemaGeneratorTestUnit(soapLoader,
                                                                   //                             RickAndMortyExtension.class,
                                                                   //                             "ram.xsd"),
                                                                   // new SchemaGeneratorTestUnit(javaLoader,
                                                                   //                             TypedValueExtension.class,
                                                                   //                             "typed-value.xsd"),
                                                                   new SchemaGeneratorTestUnit(javaLoader,
                                                                                               TestOAuthExtension.class,
                                                                                               "test-oauth.xsd"));

    BiFunction<Class<?>, ExtensionModelLoader, ExtensionModel> createExtensionModel = (extension, loader) -> {
      ExtensionModel model = loadExtension(extension, loader);

      if (extensionModels.put(model.getName(), model) != null) {
        throw new IllegalArgumentException(format("Extension names must be unique. Name [%s] for extension [%s] was already used",
                                                  model.getName(), extension.getName()));
      }

      return model;
    };

    return extensions.stream()
        .map(e -> new Object[] {createExtensionModel.apply(e.getExtensionClass(), e.getLoader()), e.getFileName()})
        .collect(toList());
  }

  @Before
  public void setup() throws IOException {
    generator = new SchemaGenerator();
    expectedSchema = getResourceAsString("schemas/" + expectedXSD, getClass());
  }

  @Test
  public void generate() throws Exception {
    // XmlDslModel languageModel = extensionUnderTest.getXmlDslModel();
    // String schema = generator.generate(extensionUnderTest, languageModel, new SchemaTestDslContext());
    // compareXML(expectedSchema, schema);
    ExtensionModel muleExtensionModel = MuleExtensionModelProvider.createMuleExtensionModel();
    String schema = generator.generate(muleExtensionModel, muleExtensionModel.getXmlDslModel(), new SchemaTestDslContext());
    org.mule.runtime.core.util.FileUtils.writeStringToFile(new java.io.File("transform.xsd"), schema);
  }

  private static class SchemaTestDslContext implements DslResolvingContext {

    @Override
    public Optional<ExtensionModel> getExtension(String name) {
      return ofNullable(extensionModels.get(name));
    }

    @Override
    public Set<ExtensionModel> getExtensions() {
      return copyOf(extensionModels.values());
    }

    @Override
    public TypeCatalog getTypeCatalog() {
      return TypeCatalog.getDefault(copyOf(extensionModels.values()));
    }
  }

  public static ExtensionModel loadExtension(Class<?> clazz, ExtensionModelLoader loader) {
    Map<String, Object> params = new HashMap<>();
    params.put(TYPE_PROPERTY_NAME, clazz.getName());
    params.put(VERSION, getProductVersion());
    //TODO MULE-11797: as this utils is consumed from org.mule.runtime.module.extension.internal.capability.xml.schema.AbstractXmlResourceFactory.generateResource(org.mule.runtime.api.meta.model.ExtensionModel), this util should get dropped once the ticket gets implemented.
    final DslResolvingContext dslResolvingContext = getDefault(emptySet());
    return loader.loadExtensionModel(clazz.getClassLoader(), dslResolvingContext, params);
  }

  static class SchemaGeneratorTestUnit {

    final ExtensionModelLoader loader;
    final Class<?> extensionClass;
    final String fileName;

    SchemaGeneratorTestUnit(ExtensionModelLoader loader, Class<?> extensionClass, String fileName) {
      this.loader = loader;
      this.extensionClass = extensionClass;
      this.fileName = fileName;
    }

    ExtensionModelLoader getLoader() {
      return loader;
    }

    Class<?> getExtensionClass() {
      return extensionClass;
    }

    String getFileName() {
      return fileName;
    }
  }
}
