/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.resources;

import static java.util.Arrays.asList;
import static org.mule.metadata.api.model.MetadataFormat.JAVA;
import static org.mule.runtime.api.meta.Category.COMMUNITY;
import static org.mule.runtime.api.meta.ExpressionSupport.NOT_SUPPORTED;
import static org.mule.runtime.api.meta.ExpressionSupport.SUPPORTED;
import static org.mule.runtime.api.meta.model.ExecutionType.CPU_INTENSIVE;
import static org.mule.runtime.api.meta.model.parameter.ParameterRole.BEHAVIOUR;
import static org.mule.runtime.api.meta.model.parameter.ParameterRole.CONTENT;
import static org.mule.runtime.api.meta.model.parameter.ParameterRole.PRIMARY_CONTENT;
import static org.mule.runtime.core.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.internal.dsl.DslConstants.EE_NAMESPACE;
import static org.mule.runtime.internal.dsl.DslConstants.EE_PREFIX;
import org.mule.metadata.api.ClassTypeLoader;
import org.mule.metadata.api.annotation.TypeAliasAnnotation;
import org.mule.metadata.api.builder.BaseTypeBuilder;
import org.mule.metadata.java.api.annotation.ClassInformationAnnotation;
import org.mule.runtime.api.message.Attributes;
import org.mule.runtime.api.meta.MuleVersion;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.ParameterDslConfiguration;
import org.mule.runtime.api.meta.model.XmlDslModel;
import org.mule.runtime.api.meta.model.declaration.fluent.ExtensionDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.OperationDeclarer;
import org.mule.runtime.api.meta.model.declaration.fluent.ParameterGroupDeclarer;
import org.mule.runtime.api.meta.model.display.LayoutModel;
import org.mule.runtime.core.util.IOUtils;
import org.mule.runtime.extension.api.declaration.type.ExtensionsTypeLoaderFactory;
import org.mule.runtime.extension.api.declaration.type.annotation.ExtensionTypeAnnotationsRegistry;
import org.mule.runtime.extension.api.persistence.ExtensionModelJsonSerializer;
import org.mule.runtime.extension.internal.loader.DefaultExtensionLoadingContext;
import org.mule.runtime.extension.internal.loader.ExtensionModelFactory;
import org.mule.runtime.module.extension.internal.util.NullDslResolvingContext;

import java.util.Map;

/**
 * Utility class to access the {@link ExtensionModel} definition for Mule's Runtime
 *
 * @since 4.0
 */
public class MuleExtensionModelProvider {

  private static final String MODEL_JSON = "META-INF/mule-extension-model.json";
  private static final String JSON = IOUtils.toString(MuleExtensionModelProvider.class.getClassLoader()
                                                        .getResourceAsStream(MODEL_JSON));
  private static final ExtensionModel EXTENSION_MODEL =
    withContextClassLoader(ExtensionTypeAnnotationsRegistry.class.getClassLoader(),
                           () -> new ExtensionModelJsonSerializer(false).deserialize(JSON));


  /**
   * @return the {@link ExtensionModel} definition for Mule's Runtime
   */
  public static ExtensionModel getMuleExtensionModel() {
    return EXTENSION_MODEL;
  }

  public static ExtensionModel createMuleExtensionModel() {

    ExtensionDeclarer extensionDeclarer = new ExtensionDeclarer()
      .named("Mule EE")
      .describedAs("Mule Runtime and Integration Platform: Core components")
      .onVersion("4.0.0-SNAPSHOT")
      .fromVendor("MuleSoft, Inc.")
      .withCategory(COMMUNITY)
      .withMinMuleVersion(new MuleVersion("4.0.0-SNAPSHOT"))
      .withXmlDsl(XmlDslModel.builder()
                    .setPrefix(EE_PREFIX)
                    .setNamespace(EE_NAMESPACE)
                    .setSchemaVersion("4.0.0-SNAPSHOT")
                    .setXsdFileName("mule-ee.xsd")
                    .build());

    declareTransform(extensionDeclarer);

    return new ExtensionModelFactory()
      .create(new DefaultExtensionLoadingContext(extensionDeclarer,
                                                 Thread.currentThread().getContextClassLoader(),
                                                 new NullDslResolvingContext()));

  }

  private static void declareTransform(ExtensionDeclarer extensionDeclarer) {
    final BaseTypeBuilder typeBuilder = BaseTypeBuilder.create(JAVA);
    final ClassTypeLoader typeLoader =
      ExtensionsTypeLoaderFactory.getDefault().createTypeLoader(MuleExtensionModelProvider.class.getClassLoader());

    OperationDeclarer transform = extensionDeclarer
      .withOperation("transform")
      .withExecutionType(CPU_INTENSIVE);

    transform.withOutput().ofType(typeBuilder.anyType().build());
    transform.withOutputAttributes().ofType(typeLoader.load(Attributes.class));

    ParameterGroupDeclarer message = transform
      .onParameterGroup("Message")
      .withDslInlineRepresentation(true)
      .withLayout(LayoutModel.builder().order(1).build());

    message
      .withOptionalParameter("setPayload")
      .withRole(PRIMARY_CONTENT)
      .describedAs("Modifies the payload of the message according to the provided value")
      .withExpressionSupport(SUPPORTED)
      .withLayout(LayoutModel.builder().tabName("Set Payload").build())
      .withDsl(ParameterDslConfiguration.builder()
                 .allowsInlineDefinition(true)
                 .allowsReferences(false)
                 .allowTopLevelDefinition(false)
                 .build())
      .ofType(typeLoader.load(Object.class));

    message
      .withOptionalParameter("payloadResource")
      .withRole(BEHAVIOUR)
      .describedAs("Modifies the payload of the message according to the value referenced as a resource")
      .withExpressionSupport(NOT_SUPPORTED)
      .withLayout(LayoutModel.builder().tabName("Set Payload").build())
      .withDsl(ParameterDslConfiguration.builder()
                 .allowsInlineDefinition(false)
                 .allowsReferences(false)
                 .allowTopLevelDefinition(false)
                 .build())
      .ofType(typeLoader.load(String.class));

    message
      .withOptionalParameter("setAttributes")
      .withRole(CONTENT)
      .describedAs("Modifies the attributes of the message according to the provided value")
      .withExpressionSupport(SUPPORTED)
      .withLayout(LayoutModel.builder().tabName("Set Attributes").build())
      .withDsl(ParameterDslConfiguration.builder()
                 .allowsInlineDefinition(true)
                 .allowsReferences(false)
                 .allowTopLevelDefinition(false)
                 .build())
      .ofType(typeLoader.load(Object.class));

    message
      .withOptionalParameter("attributesResource")
      .withRole(BEHAVIOUR)
      .describedAs("Modifies the attributes of the message according to the value referenced as a resource")
      .withExpressionSupport(NOT_SUPPORTED)
      .withLayout(LayoutModel.builder().tabName("Set Attributes").build())
      .withDsl(ParameterDslConfiguration.builder()
                 .allowsInlineDefinition(false)
                 .allowsReferences(false)
                 .allowTopLevelDefinition(false)
                 .build())
      .ofType(typeLoader.load(String.class));

    ParameterGroupDeclarer setVariables = transform
      .onParameterGroup("Set Variables")
      .withLayout(LayoutModel.builder().order(2).build());

    setVariables
      .withOptionalParameter("setVariables")
      .ofType(typeBuilder.objectType()
                .id(Map.class.getName())
                .openWith(typeLoader.load(String.class))
                .with(new ClassInformationAnnotation(Map.class, asList(String.class, String.class)))
                .with(new TypeAliasAnnotation("SetVariables"))
                .build())
      .withDsl(ParameterDslConfiguration.builder()
                 .allowsInlineDefinition(true)
                 .allowsReferences(false)
                 .allowTopLevelDefinition(false).build())
      .withExpressionSupport(NOT_SUPPORTED)
      .withRole(BEHAVIOUR);
  }
  
}
