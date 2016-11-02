/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.module.extension.internal.runtime.operation;

import static java.lang.String.format;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.disposeIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.initialiseIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.startIfNeeded;
import static org.mule.runtime.core.api.lifecycle.LifecycleUtils.stopIfNeeded;
import static org.mule.runtime.core.el.mvel.MessageVariableResolverFactory.FLOW_VARS;
import static org.mule.runtime.core.util.ClassUtils.withContextClassLoader;
import static org.mule.runtime.core.util.StringUtils.isBlank;
import static org.mule.runtime.module.extension.internal.util.IntrospectionUtils.isVoid;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getClassLoader;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getInitialiserEvent;
import static org.mule.runtime.module.extension.internal.util.MuleExtensionUtils.getOperationExecutorFactory;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.message.MuleEvent;
import org.mule.runtime.api.meta.model.ExtensionModel;
import org.mule.runtime.api.meta.model.config.ConfigurationModel;
import org.mule.runtime.api.meta.model.operation.OperationModel;
import org.mule.runtime.api.meta.model.parameter.ParameterModel;
import org.mule.runtime.api.metadata.EntityMetadataProvider;
import org.mule.runtime.api.metadata.MetadataContext;
import org.mule.runtime.api.metadata.MetadataKey;
import org.mule.runtime.api.metadata.MetadataKeysContainer;
import org.mule.runtime.api.metadata.MetadataResolvingException;
import org.mule.runtime.api.metadata.descriptor.TypeMetadataDescriptor;
import org.mule.runtime.api.metadata.resolving.MetadataResult;
import org.mule.runtime.core.api.DefaultMuleException;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.api.lifecycle.InitialisationException;
import org.mule.runtime.core.api.lifecycle.Lifecycle;
import org.mule.runtime.core.api.processor.Processor;
import org.mule.runtime.core.exception.MessagingException;
import org.mule.runtime.core.policy.OperationPolicyInstance;
import org.mule.runtime.dsl.api.component.ComponentIdentifier;
import org.mule.runtime.extension.api.runtime.ConfigurationInstance;
import org.mule.runtime.extension.api.runtime.ConfigurationProvider;
import org.mule.runtime.extension.api.runtime.operation.OperationExecutor;
import org.mule.runtime.extension.api.runtime.operation.OperationExecutorFactory;
import org.mule.runtime.extension.api.runtime.operation.OperationParameter;
import org.mule.runtime.extension.api.runtime.operation.OperationParametersResolver;
import org.mule.runtime.module.extension.internal.manager.ExtensionManagerAdapter;
import org.mule.runtime.module.extension.internal.metadata.EntityMetadataMediator;
import org.mule.runtime.module.extension.internal.model.property.OperationExecutorModelProperty;
import org.mule.runtime.module.extension.internal.runtime.DefaultExecutionContext;
import org.mule.runtime.module.extension.internal.runtime.DefaultExecutionMediator;
import org.mule.runtime.module.extension.internal.runtime.ExecutionContextAdapter;
import org.mule.runtime.module.extension.internal.runtime.ExecutionMediator;
import org.mule.runtime.module.extension.internal.runtime.ExtensionComponent;
import org.mule.runtime.module.extension.internal.runtime.LazyOperationContext;
import org.mule.runtime.module.extension.internal.runtime.ParameterValueResolver;
import org.mule.runtime.module.extension.internal.runtime.resolver.ExtensionResolverSetVisitor;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSet;
import org.mule.runtime.module.extension.internal.runtime.resolver.ResolverSetResult;
import org.mule.runtime.module.extension.internal.runtime.resolver.ValueResolver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import org.slf4j.Logger;

/**
 * A {@link Processor} capable of executing extension operations.
 * <p>
 * It obtains a configuration instance, evaluate all the operation parameters and executes a {@link OperationModel} by using a
 * {@link #operationExecutor}. This message processor is capable of serving the execution of any {@link OperationModel} of any
 * {@link ExtensionModel}.
 * <p>
 * A {@link #operationExecutor} is obtained by testing the {@link OperationModel} for a {@link OperationExecutorModelProperty}
 * through which a {@link OperationExecutorFactory} is obtained. Models with no such property cannot be used with this class. The
 * obtained {@link OperationExecutor} serve all invocations of {@link #process(Event)} on {@code this} instance but will not be
 * shared with other instances of {@link OperationMessageProcessor}. All the {@link Lifecycle} events that {@code this} instance
 * receives will be propagated to the {@link #operationExecutor}.
 * <p>
 * The {@link #operationExecutor} is executed directly but by the means of a {@link DefaultExecutionMediator}
 *
 * @since 3.7.0
 */
public class OperationMessageProcessor extends ExtensionComponent implements Processor, EntityMetadataProvider, Lifecycle {

  private static final Logger LOGGER = getLogger(OperationMessageProcessor.class);
  static final String INVALID_TARGET_MESSAGE =
          "Flow '%s' defines an invalid usage of operation '%s' which uses %s as target";

  private final ExtensionModel extensionModel;
  private final OperationModel operationModel;
  private final ResolverSet resolverSet;
  private final String target;
  private final EntityMetadataMediator entityMetadataMediator;
  private final List<OperationParameter> operationParameters;

  private ExecutionMediator executionMediator;
  private OperationExecutor operationExecutor;
  private OperationParametersResolver operationParametersResolver;
  protected ReturnDelegate returnDelegate;

  public OperationMessageProcessor(ExtensionModel extensionModel,
                                   OperationModel operationModel,
                                   ConfigurationProvider configurationProvider,
                                   String target,
                                   ResolverSet resolverSet,
                                   ExtensionManagerAdapter extensionManager, List<OperationParameter> operationParameters) {
    super(extensionModel, operationModel, configurationProvider, extensionManager);
    this.extensionModel = extensionModel;
    this.operationModel = operationModel;
    this.resolverSet = resolverSet;
    this.target = target;
    this.entityMetadataMediator = new EntityMetadataMediator(operationModel);
    this.operationParameters = operationParameters;
  }

  @Override
  public Event process(Event event) throws MuleException {
    return withContextClassLoader(getExtensionClassLoader(), () -> {
      Optional<ConfigurationInstance> configuration = getConfiguration(event);
      ExecutionContextAdapter operationContext = createExecutionContext(configuration, event);
      ResolverSetResult resolverSetResult = this.resolverSet.resolve(event);

      //TODO at this point we need to know which are the set of parameters that come from the configuration including those with
      //default values which most likely are expressions like #[payload]. For each of this parameters we must:
      // - Evaluate the values if they are expressions
      // - Convert the parameters values to the expected value types
      // - Correlate the parameter with it's configuration attribute in the config so we can report any error back to the user or display the values when doing debugging
      // - Assign the value to each parameter so we can call back to the "operation mechanism" to actually invoke the operation

      List<ResolvedOperationParameter> resolvedOperationParameters = new ArrayList<ResolvedOperationParameter>();
      resolverSetResult.asMap()
              .entrySet().stream().forEach( (entry) -> {
        ParameterModel foundParameterModel = operationModel.getParameterModels().stream()
                .filter(parameterModel -> parameterModel.getName().equals(entry.getKey()))
                .findAny().get();
        //if (foundParameterModel.isContent()) {
          resolvedOperationParameters.add(new ResolvedOperationParameter(entry.getKey(), entry.getValue(), foundParameterModel));
        //}
      });

      //TODO this may not be needed for now.
      //List<ResolvedOperationParameter> resolvedOperationParameters = resolveParameters(event);
      Function<Event, Event> executeOperationFunction = (policyEvent) -> {
        try
        {
          MuleEvent muleEvent = doProcess(policyEvent, operationContext);
          Event resultEvent = (Event) muleEvent;
          return resultEvent;
        }
        catch (MuleException e)
        {
          throw new MuleRuntimeException(e);
        }
      };
      Event resultEvent = null;
      boolean policyApplied = false;
      for (OperationPolicyInstance policyInstance : event.getPolicyInstances()) {
        ComponentIdentifier componentIdentifier = new ComponentIdentifier.Builder()
                .withName(operationModel.getName())
                .withNamespace("httpn")
                .build();
        if (policyInstance.getOperationPolicy().appliesToOperation(componentIdentifier)) {
          policyApplied = true;
          resultEvent = policyInstance.processOperation(
                  event, executeOperationFunction);

        }
      }
      if (!policyApplied) {
        resultEvent = executeOperationFunction.apply(event);
      }
      return resultEvent;
    }, MuleException.class, e -> {
      throw new DefaultMuleException(e);
    });
  }

  private void convert(Map<String, ValueResolver> parameters)
  {
    ExtensionResolverSetVisitor extensionResolverSetVisitor = new ExtensionResolverSetVisitor();
    for (String paramName : parameters.keySet())
    {
      ValueResolver valueResolver = parameters.get(paramName);
      valueResolver.accept(extensionResolverSetVisitor.createVisitor(paramName));
    }
  }

  private List<ResolvedOperationParameter> resolveParameters(Event event)
  {
    return null;
  }

  protected org.mule.runtime.api.message.MuleEvent doProcess(Event event, ExecutionContextAdapter operationContext)
      throws MuleException {
    Object result = executeOperation(operationContext, event);
    MuleEvent resultEvent = returnDelegate.asReturnValue(result, operationContext);
    return resultEvent;
  }

  private Object executeOperation(ExecutionContextAdapter operationContext, Event event) throws MuleException {
    try {
      return executionMediator.execute(operationExecutor, operationContext);
    } catch (MessagingException e) {
      if (e.getEvent() == null) {
        throw wrapInMessagingException(event, e);
      }
      throw e;
    } catch (Throwable e) {
      throw wrapInMessagingException(event, e);
    }
  }

  private MessagingException wrapInMessagingException(Event event, Throwable e) {
    return new MessagingException(createStaticMessage(e.getMessage()), event, e, this);
  }

  private ExecutionContextAdapter<OperationModel> createExecutionContext(Optional<ConfigurationInstance> configuration,
                                                                         Event event)
      throws MuleException {
    return new DefaultExecutionContext<>(extensionModel, configuration, resolverSet.resolve(event), operationModel, event,
                                         muleContext);
  }

  @Override
  protected void doInitialise() throws InitialisationException {
    returnDelegate = createReturnDelegate();
    //operationParametersResolver = getOperationParametersResolverFactory(operationModel).createResolver(operationModel);
    operationExecutor = getOperationExecutorFactory(operationModel).createExecutor(operationModel);
    executionMediator = createExecutionMediator();
    initialiseIfNeeded(operationExecutor, true, muleContext);
  }

  private ReturnDelegate createReturnDelegate() {
    if (isVoid(operationModel)) {
      return VoidReturnDelegate.INSTANCE;
    }

    return !isTargetPresent() ? new ValueReturnDelegate(muleContext) : new TargetReturnDelegate(target, muleContext);
  }

  private boolean isTargetPresent() {
    if (isBlank(target)) {
      return false;
    }

    if (target.startsWith(FLOW_VARS)) {
      throw new IllegalOperationException(format(INVALID_TARGET_MESSAGE, flowConstruct.getName(), operationModel.getName(),
                                                 format("the '%s' prefix", FLOW_VARS)));
    } else if (muleContext.getExpressionLanguage().isExpression(target)) {
      throw new IllegalOperationException(format(INVALID_TARGET_MESSAGE, flowConstruct.getName(), operationModel.getName(),
                                                 "an expression"));
    }

    return true;
  }

  @Override
  public void doStart() throws MuleException {
    startIfNeeded(operationExecutor);
  }

  @Override
  public void doStop() throws MuleException {
    stopIfNeeded(operationExecutor);
  }

  @Override
  public void doDispose() {
    disposeIfNeeded(operationExecutor, LOGGER);
  }

  @Override
  public MetadataResult<MetadataKeysContainer> getEntityKeys() throws MetadataResolvingException {
    final MetadataContext metadataContext = getMetadataContext();
    return withContextClassLoader(getClassLoader(this.extensionModel),
                                  () -> entityMetadataMediator.getEntityKeys(metadataContext));
  }

  @Override
  public MetadataResult<TypeMetadataDescriptor> getEntityMetadata(MetadataKey key) throws MetadataResolvingException {
    final MetadataContext metadataContext = getMetadataContext();
    return withContextClassLoader(getClassLoader(this.extensionModel),
                                  () -> entityMetadataMediator.getEntityMetadata(metadataContext, key));
  }

  protected ExecutionMediator createExecutionMediator() {
    return new DefaultExecutionMediator(extensionModel, operationModel, connectionManager);
  }

  /**
   * Validates that the {@link #operationModel} is valid for the given {@code configurationProvider}
   *
   * @throws IllegalSourceException If the validation fails
   */
  @Override
  protected void validateOperationConfiguration(ConfigurationProvider configurationProvider) {
    ConfigurationModel configurationModel = configurationProvider.getConfigurationModel();
    if (!configurationModel.getOperationModel(operationModel.getName()).isPresent() &&
        !configurationProvider.getExtensionModel().getOperationModel(operationModel.getName()).isPresent()) {
      throw new IllegalOperationException(format(
              "Flow '%s' defines an usage of operation '%s' which points to configuration '%s'. "
              + "The selected config does not support that operation.",
              flowConstruct.getName(), operationModel.getName(),
              configurationProvider.getName()));
    }
  }

  @Override
  protected ParameterValueResolver getParameterValueResolver() {
    final Event event = getInitialiserEvent(muleContext);
    return new OperationParameterValueResolver(new LazyOperationContext(resolverSet, operationModel, extensionModel, event));
  }
}
