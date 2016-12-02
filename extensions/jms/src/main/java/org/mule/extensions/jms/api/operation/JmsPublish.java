/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.extensions.jms.api.operation;

import static java.lang.String.format;
import static org.mule.extensions.jms.api.config.AckMode.AUTO;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.createProducer;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveDeliveryDelay;
import static org.mule.extensions.jms.api.operation.JmsOperationCommons.resolveOverride;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.extensions.jms.api.config.JmsConfig;
import org.mule.extensions.jms.api.config.JmsProducerConfig;
import org.mule.extensions.jms.api.connection.JmsConnection;
import org.mule.extensions.jms.api.connection.JmsSession;
import org.mule.extensions.jms.api.destination.DestinationType;
import org.mule.extensions.jms.api.exception.JmsExtensionException;
import org.mule.extensions.jms.internal.support.JmsSupport;
import org.mule.runtime.extension.api.annotation.dsl.xml.XmlHints;
import org.mule.runtime.extension.api.annotation.param.Connection;
import org.mule.runtime.extension.api.annotation.param.NullSafe;
import org.mule.runtime.extension.api.annotation.param.Optional;
import org.mule.runtime.extension.api.annotation.param.UseConfig;
import org.mule.runtime.extension.api.annotation.param.display.Summary;

import java.util.concurrent.TimeUnit;

import javax.jms.DeliveryMode;
import javax.jms.Destination;
import javax.jms.Message;
import javax.jms.MessageProducer;

import org.slf4j.Logger;

/**
 * Operation that allows the user to send a message to a JMS {@link Destination}
 *
 * @since 4.0
 */
public final class JmsPublish {

  private static final Logger LOGGER = getLogger(JmsPublish.class);


  /**
   * Operation that allows the user to send a {@link Message} to a JMS {@link Destination
   *
   * @param config             the current {@link JmsProducerConfig }
   * @param connection         the current {@link JmsConnection}
   * @param destination        the name of the {@link Destination} where the {@link Message} should be sent
   * @param type               the {@link DestinationType} of the {@code destination}
   * @param messageBuilder     the {@link MessageBuilder} used to create the {@link Message} to be sent
   * @param persistentDelivery {@code true} if {@link DeliveryMode#PERSISTENT} should be used
   * @param priority           the {@link Message#getJMSPriority} to be set
   * @param timeToLive         the time the message will be in the broker before it expires and is discarded
   * @param timeToLiveUnit     unit to be used in the timeToLive configurations
   * @param deliveryDelay      Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery
   * @param deliveryDelayUnit  Time unit to be used in the deliveryDelay configurations
   * @throws JmsExtensionException if an error occurs
   */
  public void publish(@UseConfig JmsConfig config, @Connection JmsConnection connection,
                      @XmlHints(
                          allowReferences = false) @Summary("The name of the Destination where the Message should be sent") String destination,
                      @Optional(defaultValue = "QUEUE") @Summary("The type of the Destination") DestinationType destinationType,
                      @Optional @NullSafe @Summary("A builder for the message that will be published") MessageBuilder messageBuilder,
                      @Optional @Summary("If true, the Message will be sent using the PERSISTENT JMSDeliveryMode") Boolean persistentDelivery,
                      @Optional @Summary("The default JMSPriority value to be used when sending the message") Integer priority,
                      @Optional @Summary("Defines the default time the message will be in the broker before it expires and is discarded") Long timeToLive,
                      @Optional @Summary("Time unit to be used in the timeToLive configurations") TimeUnit timeToLiveUnit,
                      // JMS 2.0
                      @Optional @Summary("Only used by JMS 2.0. Sets the delivery delay to be applied in order to postpone the Message delivery") Long deliveryDelay,
                      @Optional @Summary("Time unit to be used in the deliveryDelay configurations") TimeUnit deliveryDelayUnit)
      throws JmsExtensionException {

    JmsProducerConfig producerConfig = config.getProducerConfig();

    java.util.Optional<Long> delay = resolveDeliveryDelay(connection.getJmsSupport().getSpecification(),
                                                          producerConfig, deliveryDelay, deliveryDelayUnit);
    persistentDelivery = resolveOverride(producerConfig.isPersistentDelivery(), persistentDelivery);
    priority = resolveOverride(producerConfig.getPriority(), priority);
    timeToLive = resolveOverride(producerConfig.getTimeToLiveUnit(), timeToLiveUnit)
        .toMillis(resolveOverride(producerConfig.getTimeToLive(), timeToLive));

    try {
      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug("Begin publish");
      }

      JmsSession session = connection.createSession(AUTO, destinationType.isTopic());
      Message message = messageBuilder.build(connection.getJmsSupport(), session.get(), config);

      if (LOGGER.isDebugEnabled()) {
        LOGGER.debug(format("Message built, sending message to %s", destination));
      }

      JmsSupport jmsSupport = connection.getJmsSupport();
      Destination jmsDestination = jmsSupport.createDestination(session.get(), destination, destinationType.isTopic());

      MessageProducer producer =
          createProducer(connection, producerConfig, destinationType.isTopic(), session.get(), delay, jmsDestination, LOGGER);
      jmsSupport.send(producer, message, jmsDestination, persistentDelivery, priority, timeToLive, destinationType.isTopic());

    } catch (Exception e) {
      LOGGER.error("An error occurred while sending a message: ", e);
      throw new JmsExtensionException(createStaticMessage("An error occurred while sending a message to [%s]: ", destination), e);
    }
  }

}