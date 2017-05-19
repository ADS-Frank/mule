/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.runtime.core.api.util;

import static org.mule.runtime.core.api.rx.Exceptions.rxExceptionToMuleException;
import static reactor.core.Exceptions.unwrap;
import org.mule.runtime.api.exception.MuleException;
import org.mule.runtime.api.message.Message;
import org.mule.runtime.api.streaming.Cursor;
import org.mule.runtime.api.streaming.CursorProvider;
import org.mule.runtime.api.util.Reference;
import org.mule.runtime.core.api.Event;
import org.mule.runtime.core.streaming.CursorProviderFactory;
import org.mule.runtime.core.util.func.CheckedFunction;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Utilities for handling {@link Cursor} instances
 *
 * @since 4.0
 */
public final class StreamingUtils {

  /**
   * Executes the given function {@code f} considering that the given {@code event} might have
   * a {@link CursorProvider} as payload. In that case, this method obtains a cursor from the provider
   * and executes the function.
   * <p>
   * Closing the opened cursor, handling exceptions and return values are all taken care of by this utility
   * method.
   *
   * @param event an {@link Event}
   * @param f     the function to execute
   * @return the output {@link Event}
   * @throws MuleException
   */
  public static Event withCursoredEvent(Event event, CheckedFunction<Event, Event> f) throws MuleException {
    if (event.getMessage().getPayload() == null) {
      return event;
    }
    Reference<Throwable> exception = new Reference<>();
    CheckedFunction<Event, Event> function = new CheckedFunction<Event, Event>() {

      @Override
      public Event applyChecked(Event event) throws Throwable {
        return f.apply(event);
      }

      @Override
      public Event handleException(Throwable throwable) {
        exception.set(unwrap(throwable));
        return null;
      }
    };

    Object payload = event.getMessage().getPayload().getValue();
    CursorProvider cursorProvider = null;
    Cursor cursor = null;
    try {
      if (payload instanceof CursorProvider) {
        cursorProvider = (CursorProvider) payload;
        cursor = cursorProvider.openCursor();
        event = replacePayload(event, cursor);
      }

      Event value = function.apply(event);

      if (value == null) {
        handlePossibleException(exception);
      } else if (value.getMessage().getPayload().getValue() == cursor) {
        value = replacePayload(value, cursorProvider);
      }

      return value;
    } finally {
      if (cursor != null) {
        cursor.release();
      }
    }
  }

  public static Object streamingContent(Object value, CursorProviderFactory cursorProviderFactory, Event event) {
    if (cursorProviderFactory == null) {
      return value;
    }

    if (cursorProviderFactory.accepts(value)) {
      return cursorProviderFactory.of(event, value);
    } else if (value instanceof Map) {
      addStreamingAwesomenessInEntries((Map<Object, Object>) value, cursorProviderFactory, event);
    }

    return value;
  }

  public static void addStreamingAwesomenessInEntries(Map<Object, Object> map,
                                                      CursorProviderFactory cursorProviderFactory,
                                                      Event event) {
    List<Pair<Object, Object>> streamingKeys = new LinkedList<>();

    map.entrySet().stream()
        .filter(entry -> cursorProviderFactory.accepts(entry.getValue()) || entry.getValue() instanceof Cursor)
        .forEach(entry -> {
          Object value = entry.getValue();
          value = value instanceof Cursor
              ? ((Cursor) value).getProvider()
              : cursorProviderFactory.of(event, value);

          streamingKeys.add(new Pair<>(entry.getKey(), value));
        });

    streamingKeys.forEach(p -> map.put(p.getFirst(), p.getSecond()));
  }

  public static <K, V> void resolveCursorProvidersInEntries(Map<K, V> map) {
    List<Pair> streamingKeys = new LinkedList<>();

    map.entrySet().stream()
        .filter(entry -> entry.getValue() instanceof CursorProvider)
        .forEach(entry -> streamingKeys.add(new Pair(entry.getKey(), ((CursorProvider) entry.getValue()).openCursor())));

    streamingKeys.forEach(p -> map.put((K) p.getFirst(), (V) p.getSecond()));
  }

  private static Event replacePayload(Event event, Object newPayload) {
    return Event.builder(event)
        .message(Message.builder(event.getMessage())
                     .payload(newPayload)
                     .build())
        .build();
  }

  private static void handlePossibleException(Reference<Throwable> exception) throws MuleException {
    Throwable t = exception.get();
    if (t != null) {
      throw rxExceptionToMuleException(t);
    }
  }

  private StreamingUtils() {
  }
}
