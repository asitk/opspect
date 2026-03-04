package com.opscog.kairosdb.client.builder.grouper;

import static com.opscog.kairosdb.client.util.Preconditions.checkNotNullOrEmpty;

import com.opscog.kairosdb.client.builder.Grouper;

/** Grouper that that takes custom json. */
public class CustomGrouper extends Grouper {
  private String json;

  public CustomGrouper(String name, String json) {
    super(name);
    this.json = checkNotNullOrEmpty(json);
  }

  public String toJson() {
    return "\"name\": \"" + getName() + "\", " + json;
  }
}
