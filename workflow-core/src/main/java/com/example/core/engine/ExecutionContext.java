package com.example.core.engine;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ExecutionContext implements Cloneable {
  private Map<String, Object> data = new ConcurrentHashMap<>();

  public void set(String key, Object value) {
    data.put(key, value);
  }

  @SuppressWarnings("unchecked")
  public <T> T get(String key) {
    return (T) data.get(key);
  }

  boolean contains(String key) {
    return data.containsKey(key);
  }

  public Map<String, Object> asMap() {
    return Collections.unmodifiableMap(data);
  }

  public void merge(Map<String, Object> delta) {
    if (delta != null) {
      data.putAll(delta);
    }
  }

  @Override
  public ExecutionContext clone() {
    try {
      ExecutionContext clone = (ExecutionContext) super.clone();
      clone.data = new ConcurrentHashMap<>();
      clone.data.putAll(this.data);
      return clone;
    } catch (CloneNotSupportedException e) {
      throw new AssertionError("Clone not supported", e);
    }
  }

  public ExecutionContext deepCopy() {
    return this.clone();
  }
}
