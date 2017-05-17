/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static java.util.concurrent.TimeUnit.MINUTES;
import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Disposable;

import java.nio.ByteBuffer;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.slf4j.Logger;

/**
 * {@link ByteBufferManager} implementation which pools instances for better performance.
 * <p>
 * Buffers are kept in separate pools depending on their capacity.
 * <p>
 * Idle buffers and capacity pools are automatically expired.
 *
 * @since 4.0
 */
public class PoolingByteBufferManager implements ByteBufferManager, Disposable {

  private static final Logger LOGGER = getLogger(PoolingByteBufferManager.class);
  private static final long ONE_MINUTE = MINUTES.toMillis(1);

  private final KeyedObjectPool<Integer, ByteBuffer> pool = createPool();

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteBuffer allocate(int capacity) {
    try {
      return pool.borrowObject(capacity);
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage("Could not allocate byte buffer"), e);
    }
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deallocate(ByteBuffer byteBuffer) {
    try {
      pool.returnObject(byteBuffer.capacity(), byteBuffer);
    } catch (Exception e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Could not deallocate buffer of capacity " + byteBuffer.capacity(), e);
      }
    }
  }

  @Override
  public void dispose() {
    try {
      pool.clear();
    } catch (Exception e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Error disposing pool of byte buffers", e);
      }
    }
  }

  private KeyedObjectPool<Integer, ByteBuffer> createPool() {
    GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
    config.setMaxTotalPerKey(-1);
    config.setBlockWhenExhausted(true);
    config.setTestOnBorrow(false);
    config.setSoftMinEvictableIdleTimeMillis(ONE_MINUTE);
    config.setTimeBetweenEvictionRunsMillis(ONE_MINUTE);
    config.setTestOnReturn(false);
    config.setTestWhileIdle(false);

    return new GenericKeyedObjectPool<>(new BaseKeyedPooledObjectFactory<Integer, ByteBuffer>() {

      @Override
      public ByteBuffer create(Integer capacity) throws Exception {
        return ByteBuffer.allocate(capacity);
      }

      @Override
      public PooledObject<ByteBuffer> wrap(ByteBuffer value) {
        return new DefaultPooledObject<>(value);
      }

      @Override
      public void activateObject(Integer key, PooledObject<ByteBuffer> p) throws Exception {
        clear(p);
      }

      @Override
      public void passivateObject(Integer key, PooledObject<ByteBuffer> p) throws Exception {
        clear(p);
      }

      private void clear(PooledObject<ByteBuffer> p) {
        p.getObject().clear();
      }
    }, config);
  }
}
