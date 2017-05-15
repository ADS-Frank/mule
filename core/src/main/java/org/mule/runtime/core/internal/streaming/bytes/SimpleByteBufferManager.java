/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.internal.streaming.bytes;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple implementation of {@link ByteBufferManager}
 *
 * @since 4.0
 */
public class SimpleByteBufferManager implements ByteBufferManager {

  private final AtomicLong totalBufferMemory = new AtomicLong(0);

  /**
   * {@inheritDoc}
   */
  @Override
  public ByteBuffer allocate(int capacity) {
    ByteBuffer buffer = ByteBuffer.allocate(capacity);
    totalBufferMemory.addAndGet(capacity);

    return buffer;
  }

  /**
   * No - Op operation
   * {@inheritDoc}
   */
  @Override
  public void deallocate(ByteBuffer byteBuffer) {
    totalBufferMemory.addAndGet(-byteBuffer.capacity());
  }
}
