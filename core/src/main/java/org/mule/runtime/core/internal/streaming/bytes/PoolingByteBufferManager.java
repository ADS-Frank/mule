/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import static org.mule.runtime.api.i18n.I18nMessageFactory.createStaticMessage;
import static org.slf4j.LoggerFactory.getLogger;
import org.mule.runtime.api.exception.MuleRuntimeException;
import org.mule.runtime.api.lifecycle.Disposable;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.pool2.BaseKeyedPooledObjectFactory;
import org.apache.commons.pool2.BasePooledObjectFactory;
import org.apache.commons.pool2.KeyedObjectPool;
import org.apache.commons.pool2.PooledObject;
import org.apache.commons.pool2.impl.DefaultPooledObject;
import org.apache.commons.pool2.impl.GenericKeyedObjectPool;
import org.apache.commons.pool2.impl.GenericKeyedObjectPoolConfig;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
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
  private static final int UNBOUNDED = -1;
  private static final int MAX_IDLE = Runtime.getRuntime().availableProcessors();

  private AtomicLong borrows = new AtomicLong(0);
  private AtomicLong returns = new AtomicLong(0);

  private final KeyedObjectPool<Integer, ByteBuffer> pool = createPool();

  //private final LoadingCache<Integer, ObjectPool<ByteBuffer>> pools = CacheBuilder.newBuilder()
  //    .expireAfterAccess(10, SECONDS)
  //    .removalListener((RemovalListener<Integer, ObjectPool<ByteBuffer>>) notification -> {
  //      try {
  //        notification.getValue().close();
  //      } catch (Exception e) {
  //        if (LOGGER.isDebugEnabled()) {
  //          LOGGER.debug("Found exception trying to dispose buffer pool for capacity " + notification.getKey(), e);
  //        }
  //      }
  //    }).build(new CacheLoader<Integer, ObjectPool<ByteBuffer>>() {
  //
  //      @Override
  //      public ObjectPool<ByteBuffer> load(Integer capacity) throws Exception {
  //        return createPool(capacity);
  //      }
  //    });

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteBuffer allocate(int capacity) {
    //try {
    //  ByteBuffer b = pools.getUnchecked(capacity).borrowObject();
    //  //borrows.addAndGet(1);
    //  //log("BORROWING: -->");
    //  return b;
    //} catch (Exception e) {
    //  throw new MuleRuntimeException(createStaticMessage("Could not allocate byte buffer"), e);
    //}
    try {
      return pool.borrowObject(capacity);
    } catch (Exception e) {
      throw new MuleRuntimeException(createStaticMessage("Could not allocate byte buffer"), e);
    }
  }

  private void log(String prefix) {
    long borrows = this.borrows.get();
    long returns = this.returns.get();
    long delta = borrows - returns;

    System.out.println(String.format("%s Borrows: %d | returns: %d | delta %d", prefix, borrows, returns, delta));
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void deallocate(ByteBuffer byteBuffer) {
    //int capacity = byteBuffer.capacity();
    //ObjectPool<ByteBuffer> pool = pools.getIfPresent(capacity);
    //if (pool != null) {
    //  try {
    //    if (pool.getNumIdle() > MAX_IDLE) {
    //      pool.invalidateObject(byteBuffer);
    //    } else {
    //      pool.returnObject(byteBuffer);
    //    }
    //  } catch (Exception e) {
    //    if (LOGGER.isDebugEnabled()) {
    //      LOGGER.debug("Could not deallocate buffer of capacity " + capacity, e);
    //    }
    //  }
    //  //returns.addAndGet(1);
    //  //log("RETURNING: -->");
    //}

    final int capacity = byteBuffer.capacity();
    try {
      if (pool.getNumIdle(capacity) > MAX_IDLE) {
        pool.invalidateObject(capacity, byteBuffer);
      } else {
        pool.returnObject(capacity, byteBuffer);
      }
    } catch (Exception e) {
      //if (LOGGER.isWarnEnabled()) {
      //  LOGGER.warn("Could not deallocate buffer of capacity " + byteBuffer.capacity(), e);
      //}
      throw new RuntimeException(e);
    }
  }

  @Override
  public void dispose() {
    try {
      pool.clear();
      //pools.invalidateAll();
    } catch (Exception e) {
      if (LOGGER.isWarnEnabled()) {
        LOGGER.warn("Error disposing pool of byte buffers", e);
      }
    }
  }

  private KeyedObjectPool<Integer, ByteBuffer> createPool() {
    GenericKeyedObjectPoolConfig config = new GenericKeyedObjectPoolConfig();
    config.setMaxTotalPerKey(UNBOUNDED);
    config.setMaxTotal(UNBOUNDED);
    config.setBlockWhenExhausted(false);
    config.setTestOnBorrow(false);
    config.setTestOnReturn(false);
    config.setTestWhileIdle(false);
    config.setTestOnCreate(false);
    config.setJmxEnabled(false);

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
        p.getObject().clear();
      }

      @Override
      public void passivateObject(Integer key, PooledObject<ByteBuffer> p) throws Exception {
        p.getObject().clear();
      }
    }, config);
  }

  private org.apache.commons.pool2.ObjectPool<ByteBuffer> createPool(int capacity) {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setMaxIdle(MAX_IDLE);
    config.setMaxTotal(-1);
    config.setBlockWhenExhausted(false);
    config.setBlockWhenExhausted(false);
    config.setTestOnBorrow(false);
    config.setTestOnReturn(false);
    config.setTestWhileIdle(false);
    config.setTestOnCreate(false);
    config.setJmxEnabled(false);

    return new org.apache.commons.pool2.impl.GenericObjectPool<>(new BasePooledObjectFactory<ByteBuffer>() {

      @Override
      public ByteBuffer create() throws Exception {
        return ByteBuffer.allocate(capacity);
      }

      @Override
      public PooledObject<ByteBuffer> wrap(ByteBuffer obj) {
        return new DefaultPooledObject<>(obj);
      }

      @Override
      public void activateObject(PooledObject<ByteBuffer> p) throws Exception {
        p.getObject().clear();
      }
    }, config);
    //GenericObjectPool.Config config = new GenericObjectPool.Config();
    //config.maxIdle = DEFAULT_MAX_IDLE;
    //config.maxActive = -1;
    //config.maxWait = DEFAULT_MAX_WAIT;
    //config.whenExhaustedAction = WHEN_EXHAUSTED_GROW;
    //config.minEvictableIdleTimeMillis = ONE_MINUTE;
    //config.timeBetweenEvictionRunsMillis = ONE_MINUTE;
    //config.testOnBorrow = false;
    //config.testOnReturn = false;
    //config.testWhileIdle = false;
    //GenericObjectPool genericPool = new GenericObjectPool(new ByteBufferObjectFactory(capacity), config);

  }
}
